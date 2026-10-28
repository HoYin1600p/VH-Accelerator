package dev.hoyin1600p.vhaccelerator.startup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;

/** Bounded, server-independent startup records. No game objects are retained. */
public final class ServerStartupReport {
    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_RECORDS = 4096;
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final List<String> REQUIRED_CHECKPOINTS = List.of(
            "attachment", "construction", "registry_start", "registry_end",
            "common_setup", "sided_setup", "load_complete", "reload_start",
            "reload_complete", "dedicated_initialization", "level_construction",
            "spawn_preparation", "done", "server_starting", "server_started");

    private final String runId = UUID.randomUUID().toString();
    private final ClockMapping clock;
    private final boolean detailed;
    private final int limit;
    private final List<Event> events = new ArrayList<>();
    private final Map<String, com.google.gson.JsonElement> observations = new LinkedHashMap<>();
    private com.google.gson.JsonElement lifecycleLedger;
    private final StartupOperationMetrics operations = new StartupOperationMetrics();
    // Compatibility callbacks may hold their group monitor while reporting failure. Never acquire
    // this report's monitor here: serialization acquires the report monitor before group snapshots.
    private final Object diagnosticFailureLock = new Object();
    private final java.util.Set<StartupDiagnostics.Failure> diagnosticFailures = new java.util.LinkedHashSet<>();
    private volatile boolean diagnosticsFailed;
    private long sequence;
    private long dropped;

    public ServerStartupReport(ClockMapping clock, boolean detailed) {
        this(clock, detailed, MAX_RECORDS);
    }

    ServerStartupReport(ClockMapping clock, boolean detailed, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("At least one startup record is required");
        }
        this.clock = clock;
        this.detailed = detailed;
        this.limit = limit;
    }

    public void checkpoint(String name, String marker) {
        long nanos = System.nanoTime();
        Instant wall = Instant.now();
        checkpointAt(name, marker, nanos, wall);
    }

    void checkpointAt(String name, String marker, long nanos, Instant wall) {
        checkpointAt(name, marker, nanos, wall, Thread.currentThread(), occupancy());
    }

    public String runId() {
        return runId;
    }

    public void enterOperation(String operation, String owner) {
        if (detailed) operations.enter(operation, owner);
    }

    public void enterOperation(String operation, String owner, String parentSpanId, Long reloadGeneration) {
        if (detailed) operations.enter(operation, owner, parentSpanId, reloadGeneration);
    }

    public void exitOperation(String operation, Long payloadBytes) {
        if (detailed) operations.exit(operation, payloadBytes);
    }

    public void exitOperation(String operation, Long payloadBytes, Long items) {
        if (detailed) operations.exit(operation, payloadBytes, items);
    }

    /** Bounded, already-scalar listener ledger from {@link LifecycleListenerLedger#toReport}. */
    public synchronized void lifecycleLedger(Map<String, ?> values) {
        lifecycleLedger = com.google.gson.JsonParser.parseString(JSON.toJson(values));
    }

    /** Values are serialized immediately: callers must supply scalar metadata, never game objects. */
    public synchronized void observation(String name, Map<String, ?> values) {
        if (!detailed) return;
        if (observations.size() >= 64 && !observations.containsKey(name)) {
            dropped++;
            return;
        }
        String encoded = JSON.toJson(values);
        if (encoded.length() > 65_536) {
            dropped++;
            return;
        }
        observations.put(name, com.google.gson.JsonParser.parseString(encoded));
    }

    synchronized void checkpointAt(String name, String marker, long nanos, Instant wall,
                                   Thread thread, Occupancy memory) {
        // A checkpoint is once-only. Later reload generations use span records instead.
        if (events.stream().anyMatch(event -> event.kind().equals("checkpoint")
                && event.name().equals(name))) {
            return;
        }
        append(new Event(nextId(), "checkpoint", name, marker, null,
                nanos - clock.jvmStartMonotonicEstimateNanos(), wall.toString(),
                thread.getId(), thread.getName(), null, null, "complete", null,
                null, null, null, null, memory));
    }

    /** Pass parent IDs explicitly so asynchronous spans keep their originating parent. */
    public synchronized Span beginSpan(String name, String parentId, Long generation, String group) {
        if (!detailed || events.size() >= limit) {
            if (detailed) {
                dropped++;
            }
            return null;
        }
        long nanos = System.nanoTime();
        Thread thread = Thread.currentThread();
        String id = nextId();
        append(new Event(id, "span_start", name, "hook", parentId,
                nanos - clock.jvmStartMonotonicEstimateNanos(), Instant.now().toString(),
                thread.getId(), thread.getName(), generation, group, "started", null,
                nanos - clock.jvmStartMonotonicEstimateNanos(), null, null, null, null));
        return new Span(id, name, parentId, generation, group, nanos, thread.getId(), cpuNanos());
    }

    public synchronized void endSpan(Span span, Throwable failure) {
        if (span == null) {
            return;
        }
        // Duplicate completions must not double-count asynchronous work.
        if (events.stream().anyMatch(event -> event.id().equals(span.id())
                && event.kind().equals("span_end"))) {
            return;
        }
        long nanos = System.nanoTime();
        Thread thread = Thread.currentThread();
        Long endingCpu = cpuNanos();
        append(new Event(span.id(), "span_end", span.name(), "hook", span.parentId(),
                nanos - clock.jvmStartMonotonicEstimateNanos(), Instant.now().toString(),
                thread.getId(), thread.getName(), span.reloadGeneration(), span.featureGroup(),
                failure == null ? "complete" : "failed",
                failure == null ? null : failure.getClass().getName(),
                span.startNanos() - clock.jvmStartMonotonicEstimateNanos(),
                nanos - clock.jvmStartMonotonicEstimateNanos(), nanos - span.startNanos(),
                thread.getId() == span.startThreadId() && span.startCpuNanos() != null
                        && endingCpu != null ? Math.max(0, endingCpu - span.startCpuNanos()) : null, null));
    }

    boolean diagnosticsFailed() { return diagnosticsFailed; }

    boolean diagnosticFailure(StartupDiagnostics.Failure failure) {
        synchronized (diagnosticFailureLock) {
            diagnosticsFailed = true;
            return diagnosticFailures.size() < 64 && diagnosticFailures.add(failure);
        }
    }

    private DiagnosticState diagnosticState() {
        synchronized (diagnosticFailureLock) {
            return new DiagnosticState(diagnosticsFailed, List.copyOf(diagnosticFailures));
        }
    }

    private record DiagnosticState(boolean failed, List<StartupDiagnostics.Failure> failures) { }

    /** Minimal output that does not consult compatibility, management beans or observation data. */
    String fallbackJson() {
        DiagnosticState diagnostics = diagnosticState();
        return "{\"schemaVersion\":1,\"runId\":\"" + runId
                + "\",\"physicalSide\":\"DEDICATED_SERVER\",\"completionState\":\"incomplete\","
                + "\"errorState\":\"startup_diagnostics_failed\",\"diagnosticFailures\":"
                + diagnostics.failures().stream().map(failure -> "{\"stage\":" + quote(failure.stage())
                        + ",\"exceptionClass\":" + quote(failure.exceptionClass())
                        + ",\"reason\":" + quote(failure.reason()) + "}")
                        .collect(java.util.stream.Collectors.joining(",", "[", "]")) + "}";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public synchronized String toJson() {
        List<String> missing = (detailed ? REQUIRED_CHECKPOINTS
                : List.of("attachment", "done", "server_started")).stream()
                .filter(name -> events.stream().noneMatch(event -> event.kind().equals("checkpoint")
                        && event.name().equals(name)))
                .toList();
        List<String> unfinished = events.stream()
                .filter(event -> event.kind().equals("span_start"))
                .filter(start -> events.stream().noneMatch(end -> end.kind().equals("span_end")
                        && end.id().equals(start.id())))
                .map(Event::id).toList();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", SCHEMA_VERSION);
        report.put("runId", runId);
        report.put("physicalSide", "DEDICATED_SERVER");
        report.put("manifest", Map.of(
                "schemaVersion", 1, "runId", runId, "processId", ProcessHandle.current().pid(),
                "javaVersion", System.getProperty("java.version", "unavailable"),
                "javaVendor", System.getProperty("java.vendor", "unavailable"),
                "vmName", System.getProperty("java.vm.name", "unavailable"),
                "externalFieldsRequired", List.of("cohort", "pair", "order", "artifact/config/script/world hashes",
                        "cache policy", "JFR clock reconciliation", "contention", "clean shutdown")));
        report.put("clockMapping", clock);
        report.put("detailedDiagnostics", detailed);
        List<String> failed = events.stream().filter(event -> event.kind().equals("span_end")
                && event.errorState() != null).map(Event::id).toList();
        var compatibility = dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups.startupSnapshot();
        boolean unsafeCompatibility = compatibility.stream().anyMatch(group -> Boolean.TRUE.equals(group.get("unsafePartialUse")));
        boolean incompleteCompatibility = compatibility.stream().anyMatch(group -> Boolean.FALSE.equals(group.get("benchmarkValid")));
        Long done = checkpointTime("done");
        Long starting = checkpointTime("server_starting");
        Long started = checkpointTime("server_started");
        boolean endpointOrderInvalid = done != null && started != null && (done > started
                || starting != null && (starting < done || starting > started));
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("state", done == null || started == null ? "missing" : endpointOrderInvalid ? "invalid" : "ordered");
        endpoints.put("doneElapsedNanos", done);
        endpoints.put("serverStartingElapsedNanos", starting);
        endpoints.put("serverStartedElapsedNanos", started);
        report.put("endpointOrder", endpoints);
        Map<String, Object> operationSummary = operations.snapshot();
        report.put("operationMetrics", operationSummary);
        report.put("executionSampleClassifier", StartupSampleClassifier.contract());
        DiagnosticState diagnostics = diagnosticState();
        report.put("completionState", missing.isEmpty() && unfinished.isEmpty() && failed.isEmpty()
                && Boolean.TRUE.equals(operationSummary.get("complete"))
                && !endpointOrderInvalid && !unsafeCompatibility && !incompleteCompatibility && dropped == 0
                && !diagnostics.failed()
                ? "complete" : "incomplete");
        report.put("diagnosticFailures", diagnostics.failures());
        report.put("errorState", diagnostics.failed() ? "startup_diagnostics_failed" : dropped != 0 ? "record_limit_exceeded" : endpointOrderInvalid
                ? "invalid_endpoint_order" : unsafeCompatibility ? "unsafe_partial_compatibility_use"
                : incompleteCompatibility ? "compatibility_application_incomplete_or_rejected"
                : !Boolean.TRUE.equals(operationSummary.get("complete")) ? "operation_measurements_incomplete"
                : failed.isEmpty() ? null : "span_failed");
        report.put("missingCheckpoints", missing);
        report.put("unfinishedSpans", unfinished);
        report.put("failedSpans", failed);
        report.put("droppedRecords", dropped);
        report.put("observations", observations);
        if (lifecycleLedger != null) report.put("lifecycleListenerLedger", lifecycleLedger);
        report.put("compatibility", compatibility);
        report.put("configurationSnapshots", dev.hoyin1600p.vhaccelerator.compat.CompatibilityConfigEvidence.snapshot());
        report.put("timingSummary", timingSummary());
        report.put("hookCoverage", Map.of(
                "classDefinitionAndTransformation", "unavailable: ModLauncher and core FML attach before VHA; use external JFR",
                "individualForgeCallbacksAndSyncQueue", "unavailable: core FML already loaded; phase barriers are observed separately",
                "mixinApplication", "VHA preApply/postApply only after timer attachment; other configs and early applications require external recording",
                "resourceBytesParseAllocationAndDuplicateIndexes", "VHA index build/read-or-map/parse operations and logical sizes only; per-resource physical reads, JSON parsing, allocation and duplicate retained indexes require external recording",
                "scriptAndGeneratedResources", "exact-version dedicated diagnostic adapters; see compatibility decisions and operationMetrics; only normal returns are timed"));
        report.put("coverage", List.of(
                "JVM start is an uptime-derived estimate; early discovery requires external recording",
                "Memory is occupancy; no collection is requested and no live-set size is inferred",
                "Span durations may overlap; their sum is not critical-path time",
                "Class counts are MXBean observations, not class-loading or transformation durations"));
        // Each event remains independently identifiable in structured log collectors.
        report.put("records", events.stream().map(event -> {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("schemaVersion", SCHEMA_VERSION);
            record.put("runId", runId);
            record.put("physicalSide", "DEDICATED_SERVER");
            record.put("event", event);
            return record;
        }).toList());
        return JSON.toJson(report);
    }

    private String nextId() {
        return Long.toString(++sequence);
    }

    private Long checkpointTime(String name) {
        return events.stream().filter(event -> event.kind().equals("checkpoint") && event.name().equals(name))
                .map(Event::elapsedNanos).findFirst().orElse(null);
    }

    private Map<String, Object> timingSummary() {
        List<Event> completed = events.stream().filter(event -> event.kind().equals("span_end")).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("wallIntervalUnionNanos", union(completed));
        result.put("inclusiveSpanSumNanos", completed.stream().mapToLong(Event::durationNanos).sum());
        result.put("interpretation", "Inclusive wall intervals overlap and include waits; task CPU is per-thread, not critical-path time");
        Map<String, List<Event>> stages = new LinkedHashMap<>();
        completed.forEach(event -> stages.computeIfAbsent(event.name(), unused -> new ArrayList<>()).add(event));
        Map<String, Object> stageSummary = new LinkedHashMap<>();
        stages.forEach((name, spans) -> stageSummary.put(name, Map.of(
                "count", spans.size(), "wallIntervalUnionNanos", union(spans),
                "inclusiveSpanSumNanos", spans.stream().mapToLong(Event::durationNanos).sum())));
        result.put("stages", stageSummary);
        Map<String, Object> threads = new LinkedHashMap<>();
        Map<String, List<Event>> threadSpans = new LinkedHashMap<>();
        completed.stream().filter(event -> event.name().endsWith(".task"))
                .forEach(event -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Long> totals = (Map<String, Long>) threads.computeIfAbsent(
                            Long.toString(event.threadId()), unused -> new LinkedHashMap<String, Long>());
                    totals.merge("taskWallNanos", event.durationNanos(), Long::sum);
                    threadSpans.computeIfAbsent(Long.toString(event.threadId()), unused -> new ArrayList<>()).add(event);
                    if (event.cpuNanos() != null) {
                        totals.merge("inclusiveTaskCpuNanos", event.cpuNanos(), Long::sum);
                        totals.merge("tasksWithCpuMeasurement", 1L, Long::sum);
                    }
                    totals.merge("taskCount", 1L, Long::sum);
                });
        threadSpans.forEach((thread, spans) -> {
            @SuppressWarnings("unchecked")
            Map<String, Long> totals = (Map<String, Long>) threads.get(thread);
            totals.put("taskWallIntervalUnionNanos", union(spans));
        });
        result.put("perThreadTasks", threads);
        result.put("waitIntervalUnionNanos", union(completed.stream()
                .filter(event -> event.name().contains("wait")).toList()));
        return result;
    }

    private static long union(List<Event> spans) {
        List<Event> ordered = spans.stream().sorted(Comparator.comparingLong(Event::startElapsedNanos)).toList();
        if (ordered.isEmpty()) {
            return 0;
        }
        long start = ordered.get(0).startElapsedNanos();
        long end = ordered.get(0).endElapsedNanos();
        long total = 0;
        for (Event span : ordered) {
            if (span.startElapsedNanos() > end) {
                total += end - start;
                start = span.startElapsedNanos();
            }
            end = Math.max(end, span.endElapsedNanos());
        }
        return total + end - start;
    }

    private static Long cpuNanos() {
        try {
            var threads = ManagementFactory.getThreadMXBean();
            if (!threads.isCurrentThreadCpuTimeSupported() || !threads.isThreadCpuTimeEnabled()) {
                return null;
            }
            long cpu = threads.getCurrentThreadCpuTime();
            return cpu < 0 ? null : cpu;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private void append(Event event) {
        if (events.size() < limit) {
            events.add(event);
        } else {
            dropped++;
        }
    }

    /** Management bean counters only; does not trigger collection, stack walks or polling. */
    private static Occupancy occupancy() {
        try {
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            MemoryUsage metaspace = ManagementFactory.getMemoryPoolMXBeans().stream()
                    .filter(pool -> pool.getName().equals("Metaspace"))
                    .map(pool -> pool.getUsage()).findFirst().orElse(null);
            long collections = 0;
            long collectionMillis = 0;
            for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
                long count = collector.getCollectionCount();
                long time = collector.getCollectionTime();
                if (count < 0 || time < 0) {
                    collections = -1;
                    collectionMillis = -1;
                    break;
                }
                collections += count;
                collectionMillis += time;
            }
            var classes = ManagementFactory.getClassLoadingMXBean();
            return new Occupancy(heap.getUsed(), heap.getCommitted(), heap.getMax(),
                    metaspace == null ? null : metaspace.getUsed(),
                    metaspace == null ? null : metaspace.getCommitted(),
                    metaspace == null ? null : metaspace.getMax(),
                    collections, collectionMillis, classes.getLoadedClassCount(),
                    classes.getTotalLoadedClassCount(), classes.getUnloadedClassCount(), null);
        } catch (RuntimeException unavailable) {
            return new Occupancy(null, null, null, null, null, null, null, null,
                    null, null, null, unavailable.getClass().getName());
        }
    }

    public record ClockMapping(long jvmStartEpochMillis, long jvmStartMonotonicEstimateNanos,
                               long attachmentMonotonicNanos, String attachmentWallClock,
                               long attachmentUptimeMillis, String resolution) { }

    public record Occupancy(Long heapUsedBytes, Long heapCommittedBytes, Long heapMaxBytes,
                            Long metaspaceUsedBytes, Long metaspaceCommittedBytes,
                            Long metaspaceMaxBytes, Long collectionCount, Long collectionTimeMillis,
                            Integer loadedClasses, Long totalLoadedClasses, Long unloadedClasses,
                            String errorState) { }

    public record Span(String id, String name, String parentId, Long reloadGeneration,
                       String featureGroup, long startNanos, long startThreadId, Long startCpuNanos) { }

    private record Event(String id, String kind, String name, String marker, String parentId,
                         long elapsedNanos, String wallClock, long threadId, String threadName,
                         Long reloadGeneration, String featureGroup, String completionState,
                         String errorState, Long startElapsedNanos, Long endElapsedNanos,
                         Long durationNanos, Long cpuNanos, Occupancy memory) { }
}
