package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts;
import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultLootCdfCompact;
import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultLootCdfLazy;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.VaultLootCdfLazyTestHost.Arm;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;

/**
 * Heap retained by the 583 startup distributions (11 tables x 1..53 rolls) eager versus lazy and compact, and
 * the first-use cost of the largest one, in the isolated original-class harness. Offline: no Sponge weaving,
 * no Forge boot. Usage: main NEW_REPORT_FILE (outside the checkout).
 */
public final class VaultLootCdfLazyMeasure {
    private VaultLootCdfLazyMeasure() { }

    public static void main(String[] args) throws Throwable {
        if (args.length != 1) throw new IllegalArgumentException("Expected NEW external report file");
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        if (Files.exists(output) || output.startsWith(Path.of("").toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Output must be new and outside the checkout");
        }
        JsonObject fixture = VaultLootCdfProductionDifferentialTest.fixture();
        List<double[]> tables = new ArrayList<>(VaultLootCdfLazyDifferentialTest.vectors(fixture));
        tables.add(new double[]{1, 2, 3, 4}); // 10 startup vectors + 1 = the 11 tables of a Wolds-sized pack
        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", 1);
        report.addProperty("scope", "isolated original Vault CDF class plus the compiled production hooks; offline, no Sponge weaving");
        report.addProperty("javaVersion", System.getProperty("java.version"));
        report.add("jvmArguments", new com.google.gson.Gson().toJsonTree(ManagementFactory.getRuntimeMXBean().getInputArguments()));
        report.addProperty("tables", tables.size());
        report.addProperty("distributions", tables.size() * 53);
        try (VaultLootCdfTestHost host = new VaultLootCdfTestHost();
             var archive = VaultLootCdfProductionDifferentialTest.originalArchive(fixture.get("jarSha256").getAsString());
             Arm eager = new Arm(archive.jar, VaultLootCdfLazyTestHost.build(archive.bytes(VaultLootCdfLayouts.CDF), true, null, false));
             Arm lazy = new Arm(archive.jar, VaultLootCdfLazyTestHost.build(archive.bytes(VaultLootCdfLayouts.CDF), true, null, true));
             Arm lazyVanilla = new Arm(archive.jar, VaultLootCdfLazyTestHost.build(archive.bytes(VaultLootCdfLayouts.CDF), false, null, true))) {
            List<Path> vhapi = VaultLootCdfLazyDifferentialTest.vhapiJars();
            host.attach(eager);
            host.attach(lazy);
            host.attach(lazyVanilla);
            host.runtime(true, true, false, Dist.DEDICATED_SERVER, true);
            VHAcceleratorConfig.COMMON.lazyCompactVaultLootCdf.set(true);
            VaultLootCdfLazyTestHost.publish(archive.version(), archive::bytes, lazy.transformed, true, false, false, true);

            // Each phase runs in its own method so no stack slot (an enhanced-for iterator, say) keeps its list alive.
            long[] eagerRun = eagerPhase(eager, tables);
            long[] lazyBoot = lazyPhase(lazy, tables, 0);
            long[] lazyAll = lazyPhase(lazy, tables, 53);
            long[] lazySmall = lazyPhase(lazy, tables, 12);
            report.addProperty("lazyDeferredAtBoot", lazyBoot[2] == 1);
            long eagerUsed = eagerRun[0];
            long eagerStructural = eagerRun[1];
            long lazyBootUsed = lazyBoot[0];
            long lazyAllUsed = lazyAll[0];
            long compactStructural = lazyAll[1];
            long lazySmallUsed = lazySmall[0];

            JsonObject heap = new JsonObject();
            heap.addProperty("eagerOriginalRetainedBytes", eagerUsed);
            heap.addProperty("eagerOriginalStructuralArrayBytes", eagerStructural);
            heap.addProperty("lazyBootRetainedBytes", lazyBootUsed);
            heap.addProperty("lazyAllUsedRetainedBytes", lazyAllUsed);
            heap.addProperty("lazyAllUsedStructuralArrayBytes", compactStructural);
            heap.addProperty("lazyRollsUpTo12UsedRetainedBytes", lazySmallUsed);
            heap.addProperty("savedAtBootBytes", eagerUsed - lazyBootUsed);
            heap.addProperty("savedWhenEverythingUsedBytes", eagerUsed - lazyAllUsed);
            heap.addProperty("compactToHashMapRatio", (double) compactStructural / eagerStructural);
            report.add("heap", heap);

            // First-use cost of the largest distribution, per owner of compute().
            JsonObject firstUse = new JsonObject();
            double[] weights = tables.get(2);
            firstUse.add("vault", firstUse(lazyVanilla, weights));
            for (Path jar : vhapi) {
                try (Arm vhapiArm = new Arm(archive.jar, VaultLootCdfLazyTestHost.build(archive.bytes(VaultLootCdfLayouts.CDF), false, jar, true))) {
                    host.attach(vhapiArm);
                    host.runtime(true, true, false, Dist.DEDICATED_SERVER, true);
                    firstUse.add(jar.getFileName().toString(), firstUse(vhapiArm, weights));
                }
            }
            // VHA's own eager builder as the computing owner.
            firstUse.add("vhaEagerBuilder", firstUse(lazy, weights));
            report.add("firstUseSamples53", firstUse);
        }
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(report);
        Files.createDirectories(output.getParent());
        Files.writeString(output, json + System.lineSeparator());
        System.out.println(json);
    }


    /** Retained bytes and structural array bytes of every distribution computed in its constructor. */
    private static long[] eagerPhase(Arm arm, List<double[]> tables) throws Throwable {
        long baseline = usedAfterGc();
        List<Object> list = build(arm, tables);
        long retained = usedAfterGc() - baseline;
        long structural = 0;
        for (int i = 0; i < list.size(); i++) structural += hashMapBytes(arm.getMap(list.get(i)));
        return new long[]{retained, structural, 0};
    }

    /** Lazy distributions; each one with up to {@code useUpTo} rolls is read once (0 = none, 53 = all). */
    private static long[] lazyPhase(Arm arm, List<double[]> tables, int useUpTo) throws Throwable {
        long baseline = usedAfterGc();
        List<Object> list = build(arm, tables);
        boolean deferred = true;
        long structural = 0;
        for (int i = 0; i < list.size(); i++) {
            Object cdf = list.get(i);
            deferred &= field(arm, cdf);
            int samples = samples(arm, cdf);
            if (samples <= useUpTo) {
                arm.get(cdf, new int[]{samples, 0, 0, 0});
                structural += ((VaultLootCdfCompact) arm.resolved(cdf)).arrayBytes();
            }
        }
        long retained = usedAfterGc() - baseline;
        return new long[]{retained, structural, deferred ? 1 : 0};
    }

    private static boolean field(Arm arm, Object cdf) {
        try { return arm.mapField(cdf) == null && arm.resolved(cdf) == null; } catch (Exception failure) { throw new AssertionError(failure); }
    }

    private static int samples(Arm arm, Object cdf) {
        try {
            Field field = arm.type().getDeclaredField("samples");
            field.setAccessible(true);
            return field.getInt(cdf);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static List<Object> build(Arm arm, List<double[]> tables) throws Throwable {
        List<Object> result = new ArrayList<>();
        for (double[] weights : tables) for (int samples = 1; samples <= 53; samples++) result.add(arm.create(samples, weights));
        return result;
    }

    /** Cold first call, then warm medians: the whole first get() (compute + proof + array) and compute() alone. */
    private static JsonObject firstUse(Arm arm, double[] weights) throws Throwable {
        List<Double> total = new ArrayList<>();
        List<Double> compute = new ArrayList<>();
        double cold = 0;
        double coldCompute = 0;
        for (int run = 0; run < 25; run++) {
            Object cdf = arm.create(53, weights);
            long[] before = VaultLootCdfLazy.statistics();
            long started = System.nanoTime();
            arm.get(cdf, new int[]{10, 20, 13, 10});
            double wall = (System.nanoTime() - started) / 1e6;
            double computeMs = (VaultLootCdfLazy.statistics()[3] - before[3]) / 1e6;
            if (run == 0) { cold = wall; coldCompute = computeMs; }
            if (run >= 5) { total.add(wall); compute.add(computeMs); }
        }
        JsonObject row = new JsonObject();
        row.addProperty("coldFirstGetMs", cold);
        row.addProperty("coldComputeMs", coldCompute);
        row.addProperty("warmMedianFirstGetMs", median(total));
        row.addProperty("warmMedianComputeMs", median(compute));
        row.addProperty("warmMedianCompactAndProofMs", median(total) - median(compute));
        row.addProperty("warmMinFirstGetMs", total.stream().mapToDouble(Double::doubleValue).min().orElse(0));
        row.addProperty("warmMaxFirstGetMs", total.stream().mapToDouble(Double::doubleValue).max().orElse(0));
        JsonArray runs = new JsonArray();
        total.forEach(runs::add);
        row.add("warmFirstGetMsRuns", runs);
        return row;
    }

    private static double median(List<Double> values) {
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return sorted[sorted.length / 2];
    }

    private static long hashMapBytes(Long2DoubleMap map) throws Exception {
        long bytes = 0;
        for (String name : List.of("key", "value")) {
            Field field = map.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object array = field.get(map);
            bytes += 16L + (long) java.lang.reflect.Array.getLength(array) * 8L;
        }
        return bytes;
    }

    private static long usedAfterGc() throws InterruptedException {
        long used = 0;
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(150);
            used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        }
        return used;
    }

    @SuppressWarnings("unused")
    private static String describe(int[] values) { return Arrays.toString(values); }
}
