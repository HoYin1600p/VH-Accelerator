package dev.hoyin1600p.vhaccelerator.startup;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.lang.management.ManagementFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ServerLaunchTimer {
    private static final java.util.concurrent.atomic.AtomicBoolean ATTACHMENT_ATTEMPTED = new java.util.concurrent.atomic.AtomicBoolean();
    private static final AtomicLong START_NANOS = new AtomicLong(-1L);
    private static final AtomicLong END_NANOS = new AtomicLong(-1L);
    private static final AtomicLong DONE_NANOS = new AtomicLong(-1L);
    private static final AtomicLong STARTED_EMISSION_NANOS = new AtomicLong(-1L);
    private static volatile ServerStartupReport report;
    private static final ThreadLocal<OperationParent> OPERATION_PARENT = new ThreadLocal<>();

    private ServerLaunchTimer() {
    }

    public static void markStart() {
        markStart(() -> {
            long attachedAtNanos = System.nanoTime();
            Instant attachedWallClock = Instant.now();
            long processUptimeMillis = Math.max(
                    0L,
                    ManagementFactory.getRuntimeMXBean().getUptime()
            );
            long processStartNanos = attachedAtNanos
                    - TimeUnit.MILLISECONDS.toNanos(processUptimeMillis);
            if (START_NANOS.compareAndSet(-1L, processStartNanos)) {
                ServerStartupReport attached = new ServerStartupReport(
                        new ServerStartupReport.ClockMapping(
                                ManagementFactory.getRuntimeMXBean().getStartTime(),
                                processStartNanos, attachedAtNanos, attachedWallClock.toString(),
                                processUptimeMillis, "uptime estimate: millisecond resolution"),
                        VHAcceleratorConfig.debugDiagnosticsEnabled());
                report = attached;
                StartupDiagnostics.attach(attached);
                StartupRecorder.launchStarted(attached.runId());
                attached.checkpointAt("attachment", "hook:ServerMainMixin/main HEAD",
                        attachedAtNanos, attachedWallClock);
                if (instrumentationEnabled()) {
                    VHAccelerator.LOGGER.info(
                            "Dedicated-server launch timer attached {} ms "
                                    + "after JVM process start",
                            processUptimeMillis
                    );
                }
            }
        });
    }

    static void markStart(Runnable attachment) {
        ATTACHMENT_ATTEMPTED.set(true);
        StartupDiagnostics.run("markStart", attachment);
    }

    /** No-op on physical clients, where the dedicated entry attachment never runs. */
    public static void checkpoint(String name, String marker) {
        StartupDiagnostics.run("checkpoint", () -> {
            ServerStartupReport active = report;
            if (active != null && VHAcceleratorConfig.debugDiagnosticsEnabled()) {
                active.checkpoint(name, marker);
            }
        });
    }

    public static ServerStartupReport.Span beginSpan(String name, String parentId,
                                                     Long generation, String group) {
        return StartupDiagnostics.get("beginSpan", () -> {
            ServerStartupReport active = report;
            return active == null ? null : active.beginSpan(name, parentId, generation, group);
        }, null);
    }

    public static void endSpan(ServerStartupReport.Span span, Throwable failure) {
        StartupDiagnostics.run("endSpan", () -> {
            ServerStartupReport active = report;
            if (active != null) {
                active.endSpan(span, failure);
            }
        });
    }

    public static boolean detailedActive() {
        return StartupDiagnostics.get("detailedActive", () -> {
            return report != null && VHAcceleratorConfig.debugDiagnosticsEnabled();
        }, false);
    }

    public static void observation(String name, java.util.Map<String, ?> values) {
        StartupDiagnostics.run("observation", () -> {
            ServerStartupReport active = report;
            if (active != null) active.observation(name, values);
        });
    }

    public static void enterOperation(String operation, String owner) {
        StartupDiagnostics.run("enterOperation", () -> {
            ServerStartupReport active = report;
            if (active != null) {
                OperationParent parent = OPERATION_PARENT.get();
                active.enterOperation(operation, owner, parent == null ? null : parent.spanId(),
                        parent == null ? null : parent.reloadGeneration());
            }
        });
    }

    static OperationParent enterTaskContext(String parentId, long generation) {
        return StartupDiagnostics.get("enterTaskContext", () -> {
            OperationParent previous = OPERATION_PARENT.get();
            OPERATION_PARENT.set(new OperationParent(parentId, generation));
            return previous;
        }, null);
    }

    static void restoreTaskContext(OperationParent previous) {
        StartupDiagnostics.run("restoreTaskContext", () -> {
            if (previous == null) OPERATION_PARENT.remove();
            else OPERATION_PARENT.set(previous);
        });
    }

    record OperationParent(String spanId, Long reloadGeneration) { }

    public static void exitOperation(String operation, Long payloadBytes) {
        StartupDiagnostics.run("exitOperation", () -> {
            ServerStartupReport active = report;
            if (active != null) active.exitOperation(operation, payloadBytes);
        });
    }

    public static void exitOperation(String operation, Long payloadBytes, Long items) {
        StartupDiagnostics.run("exitOperation", () -> {
            ServerStartupReport active = report;
            if (active != null) active.exitOperation(operation, payloadBytes, items);
        });
    }

    /** The dedicated Done emission hook supplies this independently of the started event. */
    public static void markDone() {
        StartupDiagnostics.run("markDone", () -> {
            long nanos = System.nanoTime();
            Instant wall = Instant.now();
            if (START_NANOS.get() == -1L || !DONE_NANOS.compareAndSet(-1L, nanos)) {
                return;
            }
            ServerStartupReport active = report;
            if (active != null) {
                active.checkpointAt("done", "hook:immediately before dedicated Done logger invocation", nanos, wall);
            }
        });
    }

    public static void markStartedEmission() {
        StartupDiagnostics.run("markStartedEmission", () -> {
            long nanos = System.nanoTime();
            Instant wall = Instant.now();
            ServerStartupReport active = report;
            if (active != null && STARTED_EMISSION_NANOS.compareAndSet(-1L, nanos)) {
                active.checkpointAt("server_started", "hook:ServerLifecycleHooks.handleServerStarted HEAD before event post",
                        nanos, wall);
            }
        });
    }

    public static void markEnd() {
        markEnd(ServerLaunchTimer::instrumentationEnabled,
                dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups::verifyStartupApplication,
                ServerLaunchTimer::writeReport);
    }

    static void markEnd(java.util.function.BooleanSupplier enabled, Runnable verification,
                        java.util.function.Consumer<ServerStartupReport> output) {
        StartupDiagnostics.run("markEnd", () -> {
            if (!ATTACHMENT_ATTEMPTED.get()) return; // Physical client: no dedicated attachment.
            long nanos = System.nanoTime();
            Instant wall = Instant.now();
            long emission = STARTED_EMISSION_NANOS.get();
            if (!END_NANOS.compareAndSet(-1L, emission == -1L ? nanos : emission)) return;
            ServerStartupReport completed = report;
            if (completed == null) {
                // Even attachment/config/management failures must leave an explicitly invalid run.
                completed = new ServerStartupReport(new ServerStartupReport.ClockMapping(
                        System.currentTimeMillis(), nanos, nanos, wall.toString(), 0,
                        "unavailable: startup attachment failed"), false);
                report = completed;
                StartupDiagnostics.attach(completed);
                StartupDiagnostics.failure("attachment", new IllegalStateException("startup report attachment unavailable"));
            }
            ServerStartupReport finalReport = completed;
            try {
                if (emission == -1L) {
                    StartupDiagnostics.run("endpoint.server_started", () -> finalReport.checkpointAt(
                            "server_started", "proxy:VHA listener; Forge emission hook unavailable", nanos, wall));
                }
                boolean instrumentation = StartupDiagnostics.get("startup.enabled", enabled::getAsBoolean, true);
                if (instrumentation) {
                    long start = START_NANOS.get();
                    long elapsedMillis = start < 0 ? 0 : (END_NANOS.get() - start) / 1_000_000L;
                    StartupDiagnostics.run("startup.summary", () -> VHAccelerator.LOGGER.info(
                            "Dedicated server reached ServerStartedEvent in {} ms ({})",
                            elapsedMillis, String.format("%.2f seconds", elapsedMillis / 1000.0)));
                    StartupDiagnostics.run("compatibility.verify", verification);
                }
                if (instrumentation || finalReport.diagnosticsFailed()) {
                    Runnable emit = () -> StartupDiagnostics.run("startup.output", () -> output.accept(finalReport));
                    // With the listener ledger active the report waits for the last started-event listener.
                    if (!StartupRecorder.deferOutput(finalReport, emit)) emit.run();
                }
            } finally {
                report = null; // Always release the bounded diagnostic window after finalization.
                OPERATION_PARENT.remove();
            }
        });
    }

    static ServerStartupReport currentReport() { return report; }

    /** Monotonic estimate of JVM start; falls back to now before the timer attaches. */
    static long processStartNanos() {
        long start = START_NANOS.get();
        return start == -1L ? System.nanoTime() : start;
    }

    private static boolean instrumentationEnabled() {
        try {
            return VHAcceleratorConfig.instrumentationEnabled();
        } catch (IllegalStateException configNotLoaded) {
            // ServerMainMixin runs before Forge attaches the common TOML config.
            return VHAcceleratorConfig.debugDiagnosticsEnabled();
        }
    }

    private static void writeReport(ServerStartupReport completed) {
        writeReport(completed, completed::toJson,
                () -> FMLPaths.GAMEDIR.get().resolve("logs/vhaccelerator")
                        .resolve("server-startup-" + completed.runId() + ".json"),
                (destination, json) -> {
                    Files.createDirectories(destination.getParent());
                    Files.writeString(destination, json + System.lineSeparator(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                }, json -> VHAccelerator.LOGGER.info("VHA_SERVER_STARTUP {}", json));
    }

    /** Kept separate from the game boundary so output failures can be exercised without Forge. */
    static void writeReport(ServerStartupReport completed, java.util.function.Supplier<String> serialize,
                            java.util.function.Supplier<Path> destination, ReportWriter writer,
                            java.util.function.Consumer<String> structuredLog) {
        String json;
        try {
            json = serialize.get();
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            StartupDiagnostics.failure(completed, "report.serialize", failure, ServerLaunchTimer::warnOutputFailure);
            json = completed.fallbackJson();
        }
        // Logging is part of measurement completion: record its failure before saving the file.
        if (!logReport(completed, json, structuredLog)) json = completed.fallbackJson();
        try {
            writer.write(destination.get(), json);
        } catch (IOException | RuntimeException | LinkageError | AssertionError failure) {
            StartupDiagnostics.failure(completed, "report.write", failure, ServerLaunchTimer::warnOutputFailure);
            logReport(completed, completed.fallbackJson(), structuredLog);
        }
    }

    private static boolean logReport(ServerStartupReport completed, String json,
                                     java.util.function.Consumer<String> structuredLog) {
        try {
            structuredLog.accept(json);
            return true;
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            StartupDiagnostics.failure(completed, "report.log", failure, ServerLaunchTimer::warnOutputFailure);
            return false;
        }
    }

    private static void warnOutputFailure(StartupDiagnostics.Failure detail) {
        VHAccelerator.LOGGER.warn("Startup diagnostics invalid at {}: {}: {}",
                detail.stage(), detail.exceptionClass(), detail.reason());
    }

    @FunctionalInterface
    interface ReportWriter {
        void write(Path destination, String json) throws IOException;
    }
}
