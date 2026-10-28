package dev.hoyin1600p.vhaccelerator.tools.bgtemplates;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hoyin1600p.vhaccelerator.compat.worldzones.CompactChunkMembershipMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Offline distribution-derived graph measurement. No world, registry, or server initialization. */
public final class WorldZonesMembershipMeasure {
    private static final String FIXTURE = "/dev/hoyin1600p/vhaccelerator/compat/worldzones/s4-membership-distribution.json";
    private static volatile long blackhole;
    private WorldZonesMembershipMeasure() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected a new external JSON output");
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        if (Files.exists(output) || output.startsWith(Path.of("").toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Output must be new and outside the checkout");
        }
        JsonObject fixture;
        try (var input = WorldZonesMembershipMeasure.class.getResourceAsStream(FIXTURE)) {
            if (input == null) throw new IllegalStateException("Missing pinned distribution fixture");
            fixture = JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        int[] sizes = new com.google.gson.Gson().fromJson(fixture.get("chunksPerWorldZones"), int[].class);
        if (sizes.length != 5484 || Arrays.stream(sizes).sum() != 502724) throw new AssertionError("Fixture counts");
        List<Map<String, Object>> measurements = new ArrayList<>();
        for (int repeat = 0; repeat < 3; repeat++) {
            System.gc();
            long started = System.nanoTime();
            List<Long2ObjectMap<IntSet>> original = build(sizes);
            long loadNs = System.nanoTime() - started;
            var before = RetainedGraphWalker.measure(original, Set.of());
            System.gc();
            long heapBeforeConversion = used();
            var peak = new java.util.concurrent.atomic.AtomicLong(heapBeforeConversion);
            var sampling = new java.util.concurrent.atomic.AtomicBoolean(true);
            Thread sampler = new Thread(() -> {
                while (sampling.get()) {
                    peak.accumulateAndGet(used(), Math::max);
                    try { Thread.sleep(1); } catch (InterruptedException done) { return; }
                }
            }, "zone-heap-sampler");
            var allocations = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
            if (!allocations.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Allocation counters unavailable");
            allocations.setThreadAllocatedMemoryEnabled(true);
            long allocatedBefore = allocations.getThreadAllocatedBytes(Thread.currentThread().getId());
            sampler.start();
            started = System.nanoTime();
            List<Long2ObjectMap<IntSet>> candidate = new ArrayList<>();
            long packed = 0, retained = 0;
            long convertNs;
            try {
                for (var map : original) {
                    var compact = CompactChunkMembershipMap.copyOf(map);
                    if (compact == null) throw new AssertionError("Unexpected fallback on default fixture");
                    packed += compact.inlineCount(); retained += compact.originalCount(); candidate.add(compact);
                }
                convertNs = System.nanoTime() - started;
            } finally {
                peak.accumulateAndGet(used(), Math::max); sampling.set(false); sampler.join();
            }
            long conversionAllocated = allocations.getThreadAllocatedBytes(Thread.currentThread().getId()) - allocatedBefore;
            if (packed != 502520 || retained != 204) throw new AssertionError("Packing census");
            var after = RetainedGraphWalker.measure(candidate, Set.of());
            List<Long2ObjectMap<IntSet>> both = new ArrayList<>(original); both.addAll(candidate);
            var publicationGraph = RetainedGraphWalker.measure(both, Set.of());
            verify(original, candidate, sizes);
            for (int i = 0; i < 3; i++) { query(original, sizes); query(candidate, sizes); }
            long[] baselineNs = new long[7], candidateNs = new long[7];
            for (int i = 0; i < 7; i++) {
                if ((i & 1) == 0) { baselineNs[i] = time(original, sizes); candidateNs[i] = time(candidate, sizes); }
                else { candidateNs[i] = time(candidate, sizes); baselineNs[i] = time(original, sizes); }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("repeat", repeat); row.put("originalBuildNs", loadNs); row.put("conversionNs", convertNs);
            row.put("baselineGraph", before); row.put("candidateGraph", after);
            row.put("netGraphSavingBytes", before.bytes() - after.bytes());
            row.put("conversionThreadAllocatedBytes", conversionAllocated);
            row.put("sampledConversionHeapBeforeBytes", heapBeforeConversion);
            row.put("sampledConversionPeakUsedBytes", peak.get());
            row.put("sampledConversionPeakDeltaBytes", peak.get() - heapBeforeConversion);
            row.put("sourceAndCandidatePublicationGraphBytes", publicationGraph.bytes());
            row.put("peakLimit", "1ms heap sampling is a sampled peak, not an exact maximum; co-retained publication graph is exact for the private map roots; allocation counter bounds the conversion thread, not other JVM threads");
            row.put("inlineSingletons", packed); row.put("originalFallbacks", retained);
            row.put("baselineQueryNs", baselineNs); row.put("candidateQueryNs", candidateNs);
            measurements.add(row);
            System.out.println("repeat=" + repeat + " bytes=" + before.bytes() + " -> " + after.bytes()
                    + " saved=" + (before.bytes() - after.bytes()) + " conversionMs=" + convertNs / 1e6);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scope", "Distribution-derived synthetic coordinates/IDs; exact Instrumentation strong graph rooted at private chunk maps, including all candidate indexes/fallbacks; excludes shared class metadata and authoritative/diff structures equally. Not a real-world full HPROF comparison.");
        report.put("fixture", fixture); report.put("java", System.getProperty("java.version"));
        report.put("runtimeArguments", java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        report.put("measurements", measurements);
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n",
                java.nio.file.StandardOpenOption.CREATE_NEW);
    }

    private static long key(int owner, int chunk) {
        return ((long) (chunk - 700) << 32) ^ (owner & 0xffffffffL);
    }
    private static List<Long2ObjectMap<IntSet>> build(int[] sizes) {
        List<Long2ObjectMap<IntSet>> roots = new ArrayList<>(); int pairs = 204;
        for (int owner = 0; owner < sizes.length; owner++) {
            var map = new Long2ObjectOpenHashMap<IntSet>();
            for (int j = 0; j < sizes[owner]; j++) {
                IntSet set = new IntOpenHashSet(); set.add((j % 97) - 48);
                if (pairs > 0) { set.add(1000 + pairs--); }
                map.put(key(owner, j), set);
            }
            roots.add(map);
        }
        if (pairs != 0) throw new AssertionError("Pair fixture");
        return roots;
    }
    private static void verify(List<Long2ObjectMap<IntSet>> a, List<Long2ObjectMap<IntSet>> b, int[] sizes) {
        for (int i = 0; i < sizes.length; i++) {
            if (a.get(i).size() != b.get(i).size()) throw new AssertionError("Map size");
            for (int j = 0; j < sizes[i]; j++) {
                long key = key(i, j);
                if (!Arrays.equals(a.get(i).get(key).toIntArray(), b.get(i).get(key).toIntArray())) throw new AssertionError("Member order");
            }
        }
    }
    private static long time(List<Long2ObjectMap<IntSet>> roots, int[] sizes) {
        long start = System.nanoTime(); query(roots, sizes); return System.nanoTime() - start;
    }
    private static long used() { return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); }
    private static void query(List<Long2ObjectMap<IntSet>> roots, int[] sizes) {
        long sum = 0;
        for (int i = 0; i < sizes.length; i++) for (int j = 0; j < sizes[i]; j++) {
            var iterator = roots.get(i).get(key(i, j)).iterator();
            while (iterator.hasNext()) sum = sum * 31 + iterator.nextInt();
        }
        blackhole = sum;
    }
}
