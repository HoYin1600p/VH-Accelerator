package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;

/** Separate offline timing of the exact same constructor fixture and compiled handler adapter. */
public final class VaultLootCdfOfflineTiming {
    private VaultLootCdfOfflineTiming() { }

    public static void main(String[] args) throws Throwable {
        if (args.length != 1) throw new IllegalArgumentException("Expected NEW external report file");
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        if (Files.exists(output) || output.startsWith(Path.of("").toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Output must be new and outside the checkout");
        }
        if (System.getProperty("vault.cdf.jar") == null || System.getProperty("vault.cdf.fixture") == null) {
            throw new IllegalArgumentException("Explicit original archive and fresh numeric fixture are required");
        }
        JsonObject fixture = VaultLootCdfProductionDifferentialTest.fixture();
        List<double[]> vectors = new ArrayList<>();
        fixture.getAsJsonArray("vectors").forEach(element -> {
            double[] weights = new double[4];
            for (int i = 0; i < 4; i++) weights[i] = Double.longBitsToDouble(Long.parseUnsignedLong(
                    element.getAsJsonObject().getAsJsonArray("weightBits").get(i).getAsString(), 16));
            vectors.add(weights);
        });
        int constructors = vectors.size() * 53;
        List<JsonObject> rounds = new ArrayList<>();
        try (VaultLootCdfTestHost host = new VaultLootCdfTestHost();
             var original = VaultLootCdfProductionDifferentialTest.originalArchive(fixture.get("jarSha256").getAsString());
             var candidate = new VaultLootCdfProductionDifferentialTest.OriginalArchive(original.jar, true)) {
            host.attach(candidate);
            host.runtime(true, true, false, Dist.DEDICATED_SERVER, true);
            CompatMixinGroups group = host.publish(original.version(), original::bytes, candidate.applicationTarget, true, false);
            // Equal warmup, with all comparisons outside the constructor timing loops.
            compare(batch(original, vectors).objects, batch(candidate, vectors).objects);
            for (int round = 0; round < 7; round++) {
                long originalBefore = original.originalBodyEntries();
                long candidateBefore = candidate.originalBodyEntries();
                long usesBefore = VaultLootCdfTestHost.uses(group);
                Batch left, right;
                if ((round & 1) == 0) {
                    left = batch(original, vectors); right = batch(candidate, vectors);
                } else {
                    right = batch(candidate, vectors); left = batch(original, vectors);
                }
                long checksum = compare(left.objects, right.objects);
                assertEquals(constructors, original.originalBodyEntries() - originalBefore);
                assertEquals(candidateBefore, candidate.originalBodyEntries(), "handler must bypass original compute");
                assertEquals(constructors, VaultLootCdfTestHost.uses(group) - usesBefore);
                JsonObject row = new JsonObject();
                row.addProperty("round", round); row.addProperty("originalFirst", (round & 1) == 0);
                row.addProperty("constructorsPerArm", constructors);
                row.addProperty("originalWallNanos", left.wall); row.addProperty("candidateWallNanos", right.wall);
                row.addProperty("originalThreadCpuNanos", left.cpu); row.addProperty("candidateThreadCpuNanos", right.cpu);
                row.addProperty("originalGcCount", left.gcCount); row.addProperty("candidateGcCount", right.gcCount);
                row.addProperty("originalGcMillis", left.gcMillis); row.addProperty("candidateGcMillis", right.gcMillis);
                row.addProperty("orderedRawMapChecksum", Long.toUnsignedString(checksum, 16));
                rounds.add(row);
                System.out.println("CDF_TIMING_PAIR " + row);
            }
        }
        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", 1); report.addProperty("complete", true);
        report.addProperty("scope", "JVM-warm offline original constructors versus actual compiled eager handler adapter; reflection/list overhead included");
        report.addProperty("limitations", "No Sponge weaving, Forge boot, VHAPI implementation, full generator events, startup saving or heap measurement");
        report.addProperty("javaVersion", System.getProperty("java.version"));
        report.addProperty("jarSha256", fixture.get("jarSha256").getAsString());
        report.addProperty("registrySha256", fixture.get("registrySha256").getAsString());
        report.add("jvmArguments", new com.google.gson.Gson().toJsonTree(ManagementFactory.getRuntimeMXBean().getInputArguments()));
        report.add("pairs", new com.google.gson.Gson().toJsonTree(rounds));
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n",
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private record Batch(List<VaultLootCdfProductionDifferentialTest.Oracle> objects,
                         long wall, long cpu, long gcCount, long gcMillis) { }

    private static Batch batch(VaultLootCdfProductionDifferentialTest.OriginalArchive archive,
                               List<double[]> vectors) throws Throwable {
        var objects = new ArrayList<VaultLootCdfProductionDifferentialTest.Oracle>(vectors.size() * 53);
        var thread = ManagementFactory.getThreadMXBean();
        long count = gc(false), millis = gc(true);
        long cpu = thread.isCurrentThreadCpuTimeSupported() ? thread.getCurrentThreadCpuTime() : -1;
        long start = System.nanoTime();
        for (double[] weights : vectors) for (int samples = 1; samples <= 53; samples++) {
            objects.add(archive.oracle(samples, weights));
        }
        long wall = System.nanoTime() - start;
        long elapsedCpu = cpu < 0 ? -1 : thread.getCurrentThreadCpuTime() - cpu;
        return new Batch(objects, wall, elapsedCpu, gc(false) - count, gc(true) - millis);
    }

    private static long gc(boolean millis) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean ->
                Math.max(0, millis ? bean.getCollectionTime() : bean.getCollectionCount())).sum();
    }

    private static long compare(List<VaultLootCdfProductionDifferentialTest.Oracle> left,
                                List<VaultLootCdfProductionDifferentialTest.Oracle> right) {
        assertEquals(left.size(), right.size());
        long checksum = 0xcbf29ce484222325L;
        for (int i = 0; i < left.size(); i++) {
            var expected = left.get(i).map();
            VaultLootCdfProductionDifferentialTest.assertMapIdentical(expected, right.get(i).map(), "timing pair " + i);
            for (var entry : expected.long2DoubleEntrySet()) {
                checksum = (checksum ^ entry.getLongKey()) * 0x100000001b3L;
                checksum = (checksum ^ Double.doubleToRawLongBits(entry.getDoubleValue())) * 0x100000001b3L;
            }
        }
        return checksum;
    }
}
