package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts;
import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultLootCdfCompact;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import net.minecraftforge.api.distmarker.Dist;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.VaultLootCdfLazyTestHost.Arm;

/**
 * The original Vault CDF class, eager and lazy, in every ownership arrangement the lazy hooks support:
 * Vault's own compute, VHA's eager compute, and the real VHAPI 5.0.0 and 5.8.1 overwrites.
 */
class VaultLootCdfLazyDifferentialTest {
    private static final String CDF = VaultLootCdfLayouts.CDF;

    /** Everything one scenario needs: transformed classes for each ownership arrangement. */
    private record Arms(Arm original, Arm lazyVanilla, Arm lazyEager, List<String> vhapiNames,
                        List<Arm> originalVhapi, List<Arm> lazyVhapi) implements AutoCloseable {
        @Override public void close() throws Exception {
            original.close(); lazyVanilla.close(); lazyEager.close();
            for (Arm arm : originalVhapi) arm.close();
            for (Arm arm : lazyVhapi) arm.close();
        }
    }

    static List<Path> vhapiJars() throws Exception {
        try (var files = Files.list(Path.of("libs"))) {
            List<Path> jars = files.filter(path -> path.getFileName().toString().startsWith("vhapi-")
                    && path.toString().endsWith(".jar")).sorted().toList();
            assertFalse(jars.isEmpty(), "The real VHAPI archives are required under libs (vhapi-5.0.0.jar, vhapi-5.8.1.jar)");
            return jars;
        }
    }

    private static Arms arms(VaultLootCdfProductionDifferentialTest.OriginalArchive archive) throws Exception {
        byte[] bytes = archive.bytes(CDF);
        List<Path> jars = vhapiJars();
        List<Arm> originalVhapi = new ArrayList<>();
        List<Arm> lazyVhapi = new ArrayList<>();
        for (Path jar : jars) {
            originalVhapi.add(new Arm(archive.jar, VaultLootCdfLazyTestHost.build(bytes, false, jar, false)));
            lazyVhapi.add(new Arm(archive.jar, VaultLootCdfLazyTestHost.build(bytes, false, jar, true)));
        }
        return new Arms(new Arm(archive.jar, VaultLootCdfLazyTestHost.build(bytes, false, null, false)),
                new Arm(archive.jar, VaultLootCdfLazyTestHost.build(bytes, false, null, true)),
                new Arm(archive.jar, VaultLootCdfLazyTestHost.build(bytes, true, null, true)),
                jars.stream().map(path -> path.getFileName().toString()).toList(), originalVhapi, lazyVhapi);
    }

    private static void attachAll(VaultLootCdfTestHost host, Arms arms) throws Exception {
        List<Arm> all = new ArrayList<>(List.of(arms.original(), arms.lazyVanilla(), arms.lazyEager()));
        all.addAll(arms.originalVhapi());
        all.addAll(arms.lazyVhapi());
        for (Arm arm : all) host.attach(arm);
    }

    /** Production guards pass on every lazy arrangement; activation is published exactly as postApply would. */
    private static CompatMixinGroups activate(VaultLootCdfProductionDifferentialTest.OriginalArchive archive, Arms arms)
            throws Exception {
        assertTrue(VaultLootCdfLayouts.supports(archive.version(), archive::bytes), "the gate pins the reviewed fingerprints first");
        for (Arm arm : arms.lazyVhapi()) {
            assertEquals(List.of(), VaultLootCdfLayouts.lazyApplicationProblems(arm.transformed));
        }
        assertEquals(List.of(), VaultLootCdfLayouts.lazyApplicationProblems(arms.lazyVanilla().transformed));
        assertEquals(List.of(), VaultLootCdfLayouts.applicationProblems(arms.lazyEager().transformed, false));
        CompatMixinGroups group = VaultLootCdfLazyTestHost.publish(archive.version(), archive::bytes,
                arms.lazyEager().transformed, true, false, false, true);
        assertTrue(CompatMixinGroups.activationAllowed(VaultLootCdfLayouts.LAZY_MIXIN), () -> group.snapshot().toString());
        assertTrue(CompatMixinGroups.activationAllowed(VaultLootCdfLayouts.MIXIN), () -> group.snapshot().toString());
        return group;
    }

    private static void setLazy(boolean enabled) {
        VHAcceleratorConfig.COMMON.lazyCompactVaultLootCdf.set(enabled);
    }

    @Test
    void everyStartupDistributionIsBitIdenticalForVanillaEagerAndBothVhapiOwners() throws Throwable {
        JsonObject fixture = VaultLootCdfProductionDifferentialTest.fixture();
        List<double[]> vectors = vectors(fixture);
        assertEquals(10, vectors.size());
        try (VaultLootCdfTestHost host = new VaultLootCdfTestHost();
             var archive = VaultLootCdfProductionDifferentialTest.originalArchive(fixture.get("jarSha256").getAsString());
             Arms arms = arms(archive)) {
            attachAll(host, arms);
            host.runtime(true, true, false, Dist.DEDICATED_SERVER, true);
            setLazy(true);
            activate(archive, arms);
            AtomicInteger checked = new AtomicInteger();
            AtomicInteger entries = new AtomicInteger();
            IntStream.range(0, vectors.size()).parallel().forEach(index -> {
                try {
                    for (int samples = 1; samples <= 53; samples++) {
                        checkOne(arms, vectors.get(index), samples, checked, entries);
                    }
                } catch (Throwable failure) {
                    throw new AssertionError("vector " + index + ": " + failure, failure);
                }
            });
            assertEquals(530, checked.get());
            // 10 vectors x sum of C(n + 3, 3) for n = 1..53 compositions, each read through every arrangement.
            assertEquals(10 * 395_009, entries.get());
        }
    }

    private static void checkOne(Arms arms, double[] weights, int samples, AtomicInteger checked, AtomicInteger entries)
            throws Throwable {
        Object original = arms.original().create(samples, weights);
        Object lazyVanilla = arms.lazyVanilla().create(samples, weights);
        Object lazyEager = arms.lazyEager().create(samples, weights);
        Object[] oracleVhapi = new Object[arms.originalVhapi().size()];
        Object[] lazyVhapi = new Object[oracleVhapi.length];
        for (int i = 0; i < oracleVhapi.length; i++) {
            // VHAPI's static coefficient cache is unsynchronized, and eager construction is single-threaded.
            synchronized (arms.originalVhapi().get(i)) {
                oracleVhapi[i] = arms.originalVhapi().get(i).create(samples, weights);
            }
            lazyVhapi[i] = arms.lazyVhapi().get(i).create(samples, weights);
        }
        // Deferred: nothing computed, nothing published, until the first read.
        assertNull(arms.lazyVanilla().mapField(lazyVanilla));
        assertNull(arms.lazyVanilla().resolved(lazyVanilla));
        assertNull(arms.lazyEager().mapField(lazyEager));
        for (int i = 0; i < lazyVhapi.length; i++) assertNull(arms.lazyVhapi().get(i).mapField(lazyVhapi[i]));
        // The eagerly built originals are the oracle.
        assertNotNull(arms.original().mapField(original));
        int[] count = {0};
        VaultLootCdfLazyTestHost.forEachComposition(samples, 4, frequencies -> {
            double expected = arms.original().get(original, frequencies);
            assertRaw(expected, arms.lazyVanilla().get(lazyVanilla, frequencies), "lazy vanilla");
            assertRaw(expected, arms.lazyEager().get(lazyEager, frequencies), "lazy eager");
            for (int i = 0; i < lazyVhapi.length; i++) {
                assertRaw(arms.originalVhapi().get(i).get(oracleVhapi[i], frequencies),
                        arms.lazyVhapi().get(i).get(lazyVhapi[i], frequencies), "lazy " + arms.vhapiNames().get(i));
            }
            count[0]++;
        });
        entries.addAndGet(count[0]);
        assertEquals(VaultLootCdfCompact.count(samples, 4), count[0]);
        // Inputs that are not compositions of the sample count read the map's default, as the original does.
        for (int[] odd : List.of(new int[]{0, 0, 0, 0}, new int[]{samples + 1, 0, 0, 0}, new int[]{-1, samples + 1, 0, 0},
                new int[]{samples, samples, 0, 0}, new int[]{1, 2, 3}, new int[]{samples, 0, 0, 0, 0},
                new int[]{0x10000 | samples, 0, 0, 0}, new int[]{Integer.MIN_VALUE, 0, 0, samples},
                new int[]{samples / 2, samples - samples / 2, 0, 0})) {
            double expected = arms.original().get(original, odd);
            assertRaw(expected, arms.lazyVanilla().get(lazyVanilla, odd), "odd " + Arrays.toString(odd));
            assertRaw(expected, arms.lazyEager().get(lazyEager, odd), "odd " + Arrays.toString(odd));
            for (int i = 0; i < lazyVhapi.length; i++) {
                assertRaw(arms.originalVhapi().get(i).get(oracleVhapi[i], odd), arms.lazyVhapi().get(i).get(lazyVhapi[i], odd),
                        "odd " + Arrays.toString(odd));
            }
        }
        // After the first read the distribution is the compact array and getMap is an equal read-only view.
        assertInstanceOf(VaultLootCdfCompact.class, arms.lazyVanilla().resolved(lazyVanilla));
        assertViewEqual(arms.lazyVanilla().getMap(lazyVanilla), arms.original().getMap(original));
        assertViewEqual(arms.lazyEager().getMap(lazyEager), arms.original().getMap(original));
        for (int i = 0; i < lazyVhapi.length; i++) {
            assertInstanceOf(VaultLootCdfCompact.class, arms.lazyVhapi().get(i).resolved(lazyVhapi[i]));
            assertViewEqual(arms.lazyVhapi().get(i).getMap(lazyVhapi[i]), arms.originalVhapi().get(i).getMap(oracleVhapi[i]));
        }
        // The original field stays null: the computed map was dropped after the proof.
        assertNull(arms.lazyVanilla().mapField(lazyVanilla));
        checked.incrementAndGet();
    }

    @Test
    void getMapBeforeGetAndRepeatedReadsReturnTheSameViewAndNeverRecompute() throws Throwable {
        JsonObject fixture = VaultLootCdfProductionDifferentialTest.fixture();
        double[] weights = vectors(fixture).get(2);
        try (VaultLootCdfTestHost host = new VaultLootCdfTestHost();
             var archive = VaultLootCdfProductionDifferentialTest.originalArchive(fixture.get("jarSha256").getAsString());
             Arms arms = arms(archive)) {
            attachAll(host, arms);
            host.runtime(true, true, false, Dist.DEDICATED_SERVER, true);
            setLazy(true);
            activate(archive, arms);
            Object cdf = arms.lazyVanilla().create(20, weights);
            Object oracle = arms.original().create(20, weights);
            long before = arms.lazyVanilla().computeEntries();
            Long2DoubleMap first = arms.lazyVanilla().getMap(cdf);
            assertSame(first, arms.lazyVanilla().getMap(cdf));
            assertEquals(before + 1, arms.lazyVanilla().computeEntries());
            arms.lazyVanilla().get(cdf, new int[]{20, 0, 0, 0});
            arms.lazyVanilla().get(cdf, new int[]{5, 5, 5, 5});
            assertSame(first, arms.lazyVanilla().getMap(cdf));
            assertEquals(before + 1, arms.lazyVanilla().computeEntries(), "computed exactly once");
            assertViewEqual(first, arms.original().getMap(oracle));
            assertThrows(UnsupportedOperationException.class, () -> first.put(1L, 2.0));
            assertThrows(UnsupportedOperationException.class, () -> first.remove(1L));
            assertThrows(UnsupportedOperationException.class, first::clear);
            assertThrows(UnsupportedOperationException.class,
                    () -> first.long2DoubleEntrySet().iterator().next().setValue(1.0));
            // Two instances of the same key do not share state.
            Object other = arms.lazyVanilla().create(20, weights);
            assertNull(arms.lazyVanilla().resolved(other));
            assertNotSame(first, arms.lazyVanilla().getMap(other));
        }
    }

    @Test
    void manyThreadsOnAFreshDistributionComputeOnceAndAllSeeTheSameValues() throws Throwable {
        JsonObject fixture = VaultLootCdfProductionDifferentialTest.fixture();
        double[] weights = vectors(fixture).get(0);
        try (VaultLootCdfTestHost host = new VaultLootCdfTestHost();
             var archive = VaultLootCdfProductionDifferentialTest.originalArchive(fixture.get("jarSha256").getAsString());
             Arms arms = arms(archive)) {
            attachAll(host, arms);
            host.runtime(true, true, false, Dist.DEDICATED_SERVER, true);
            setLazy(true);
            activate(archive, arms);
            List<Arm> lazy = new ArrayList<>(List.of(arms.lazyVanilla(), arms.lazyEager()));
            lazy.addAll(arms.lazyVhapi());
            for (int round = 0; round < 3; round++) {
                for (Arm arm : lazy) {
                    Object cdf = arm.create(53, weights);
                    Object oracle = (arm == arms.lazyVanilla() || arm == arms.lazyEager() ? arms.original()
                            : arms.originalVhapi().get(arms.lazyVhapi().indexOf(arm))).create(53, weights);
                    Arm oracleArm = arm == arms.lazyVanilla() || arm == arms.lazyEager() ? arms.original()
                            : arms.originalVhapi().get(arms.lazyVhapi().indexOf(arm));
                    long before = arm.computeEntries();
                    int threads = 32;
                    CountDownLatch ready = new CountDownLatch(threads);
                    CountDownLatch go = new CountDownLatch(1);
                    ExecutorService pool = Executors.newFixedThreadPool(threads);
                    try {
                        List<Future<Integer>> results = new ArrayList<>();
                        for (int t = 0; t < threads; t++) {
                            int offset = t;
                            results.add(pool.submit(() -> {
                                ready.countDown();
                                go.await();
                                int[] mismatches = {0};
                                int[] seen = {0};
                                VaultLootCdfLazyTestHost.forEachComposition(53, 4, frequencies -> {
                                    if (seen[0]++ % 997 != offset % 997 && seen[0] % 61 != 0) return;
                                    if (Double.doubleToRawLongBits(arm.get(cdf, frequencies))
                                            != Double.doubleToRawLongBits(oracleArm.get(oracle, frequencies))) mismatches[0]++;
                                });
                                // Interleave getMap with get: both publish the same single distribution.
                                if (arm.getMap(cdf).size() != 27_720) mismatches[0]++;
                                return mismatches[0];
                            }));
                        }
                        ready.await();
                        go.countDown();
                        for (Future<Integer> result : results) assertEquals(0, result.get());
                    } finally {
                        pool.shutdownNow();
                    }
                    assertEquals(before + 1, arm.computeEntries(), "compute() ran exactly once for 32 racing threads");
                    assertInstanceOf(VaultLootCdfCompact.class, arm.resolved(cdf));
                }
            }
        }
    }

    @Test
    void everyFallbackPathKeepsTheOriginalEagerHashMap() throws Throwable {
        JsonObject fixture = VaultLootCdfProductionDifferentialTest.fixture();
        double[] weights = vectors(fixture).get(2);
        try (VaultLootCdfTestHost host = new VaultLootCdfTestHost();
             var archive = VaultLootCdfProductionDifferentialTest.originalArchive(fixture.get("jarSha256").getAsString());
             Arms arms = arms(archive)) {
            attachAll(host, arms);
            host.runtime(true, true, false, Dist.DEDICATED_SERVER, true);
            setLazy(true);
            activate(archive, arms);
            record Scenario(String name, boolean master, boolean setting, boolean compare, Dist side, boolean lazySetting) { }
            for (Scenario scenario : List.of(
                    new Scenario("common master off", false, true, false, Dist.DEDICATED_SERVER, true),
                    new Scenario("lazy setting off", true, true, false, Dist.DEDICATED_SERVER, false),
                    new Scenario("Compare Mode", true, true, true, Dist.DEDICATED_SERVER, true),
                    new Scenario("physical client", true, true, false, Dist.CLIENT, true))) {
                host.runtime(scenario.master(), scenario.setting(), scenario.compare(), scenario.side(), true);
                setLazy(scenario.lazySetting());
                for (Arm arm : List.of(arms.lazyVanilla(), arms.lazyVhapi().get(0))) {
                    Object cdf = arm.create(12, weights);
                    assertNotNull(arm.mapField(cdf), scenario.name() + ": constructor computed eagerly");
                    assertNull(arm.resolved(cdf), scenario.name());
                    Object expected = (arm == arms.lazyVanilla() ? arms.original() : arms.originalVhapi().get(0)).create(12, weights);
                    Arm oracleArm = arm == arms.lazyVanilla() ? arms.original() : arms.originalVhapi().get(0);
                    assertViewEqual(arm.getMap(cdf), oracleArm.getMap(expected));
                    assertInstanceOf(Long2DoubleOpenHashMap.class, arm.getMap(cdf), "the original hash map is returned");
                    VaultLootCdfLazyTestHost.forEachComposition(12, 4, frequencies ->
                            assertRaw(oracleArm.get(expected, frequencies), arm.get(cdf, frequencies), scenario.name()));
                }
            }
            // Activation withheld or revoked: no deferral, even with every setting on.
            host.runtime(true, true, false, Dist.DEDICATED_SERVER, true);
            setLazy(true);
            CompatMixinGroups unconfirmed = VaultLootCdfLazyTestHost.publish(archive.version(), archive::bytes,
                    arms.lazyEager().transformed, true, false, false, false);
            assertFalse(CompatMixinGroups.activationAllowed(VaultLootCdfLayouts.LAZY_MIXIN));
            Object eagerOnly = arms.lazyVanilla().create(9, weights);
            assertNotNull(arms.lazyVanilla().mapField(eagerOnly), "unconfirmed application keeps the eager constructor");
            CompatMixinGroups confirmed = VaultLootCdfLazyTestHost.publish(archive.version(), archive::bytes,
                    arms.lazyEager().transformed, true, false, false, true);
            assertTrue(CompatMixinGroups.activationAllowed(VaultLootCdfLayouts.LAZY_MIXIN));
            Object deferred = arms.lazyVanilla().create(9, weights);
            assertNull(arms.lazyVanilla().mapField(deferred));
            // Late revocation stops new deferrals but keeps already-deferred distributions readable.
            assertDoesNotThrow(() -> confirmed.applicationRejected(VaultLootCdfLayouts.LAZY_MIXIN,
                    CDF.replace('/', '.'), List.of("late foreign owner")));
            assertFalse(CompatMixinGroups.activationAllowed(VaultLootCdfLayouts.LAZY_MIXIN));
            Object after = arms.lazyVanilla().create(9, weights);
            assertNotNull(arms.lazyVanilla().mapField(after));
            Object oracle = arms.original().create(9, weights);
            VaultLootCdfLazyTestHost.forEachComposition(9, 4, frequencies ->
                    assertRaw(arms.original().get(oracle, frequencies), arms.lazyVanilla().get(deferred, frequencies), "late"));
            assertNotNull(unconfirmed);
            // Seven pools and an unsupported nine-pool shape: compaction or its fallback, results identical.
            host.runtime(true, true, false, Dist.DEDICATED_SERVER, true);
            confirmedAgain(archive, arms);
            for (double[] wide : List.of(new double[]{1, 2, 3, 4, 5}, new double[]{1, 2, 3, 4, 5, 6, 7},
                    new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, new double[]{3, 1})) {
                for (int samples : new int[]{0, 1, 6, 11}) {
                    Object lazy = arms.lazyVanilla().create(samples, wide);
                    Object eager = arms.original().create(samples, wide);
                    assertNull(arms.lazyVanilla().mapField(lazy));
                    int[] count = {0};
                    VaultLootCdfLazyTestHost.forEachComposition(samples, wide.length, frequencies -> {
                        assertRaw(arms.original().get(eager, frequencies), arms.lazyVanilla().get(lazy, frequencies),
                                wide.length + " pools");
                        count[0]++;
                    });
                    assertTrue(count[0] > 0);
                    assertViewEqual(arms.lazyVanilla().getMap(lazy), arms.original().getMap(eager));
                    boolean compact = arms.lazyVanilla().resolved(lazy) instanceof VaultLootCdfCompact;
                    assertEquals(wide.length <= 8, compact, wide.length + " pools compact=" + compact);
                }
            }
        }
    }

    private static void confirmedAgain(VaultLootCdfProductionDifferentialTest.OriginalArchive archive, Arms arms)
            throws Exception {
        VaultLootCdfLazyTestHost.publish(archive.version(), archive::bytes, arms.lazyEager().transformed,
                true, false, false, true);
        assertTrue(CompatMixinGroups.activationAllowed(VaultLootCdfLayouts.LAZY_MIXIN));
    }

    @Test
    void finalVerificationRejectsSyntheticMutationsAndMissingHooks() throws Exception {
        try (var archive = VaultLootCdfProductionDifferentialTest.originalArchive(
                VaultLootCdfProductionDifferentialTest.fixture().get("jarSha256").getAsString())) {
            byte[] bytes = archive.bytes(CDF);
            assertTrue(VaultLootCdfLayouts.supports(archive.version(), archive::bytes));
            List<Path> jars = vhapiJars();
            // Positive final-boundary proof uses actual server exports in VaultLootCdfRealWeaveTest.
            // This host remains for executable semantic differentials and additional negative cases.
            // The eager group still rejects the lazy hooks on a physical client (they never apply there).
            assertFalse(VaultLootCdfLayouts.applicationProblems(
                    VaultLootCdfLazyTestHost.build(bytes, true, null, true), true).isEmpty());
            // Pristine Vault has no lazy hooks: the verification fails closed rather than passing.
            assertFalse(VaultLootCdfLayouts.lazyApplicationProblems(
                    VaultLootCdfLazyTestHost.build(bytes, false, null, false)).isEmpty());
            int rejected = 0;
            for (int mutation = 0; mutation < 14; mutation++) {
                ClassNode node = VaultLootCdfLazyTestHost.build(bytes, false, jars.get(jars.size() - 1), true);
                mutate(node, mutation);
                List<String> problems = VaultLootCdfLayouts.lazyApplicationProblems(node);
                assertFalse(problems.isEmpty(), "mutation " + mutation + " must be rejected");
                rejected++;
            }
            assertEquals(14, rejected);
        }
    }

    @Test
    void mixinRenamedVhapiLambdasAreAcceptedAndOtherMembersRejected() throws Exception {
        for (String name : List.of("md380e25$lambda$getProbability$3$0", "md380e25$lambda$compute$0$12",
                "lambda$compute$0", "mdab12$lambda$getProbability$10$3")) {
            assertTrue(VaultLootCdfLayouts.isVhapiSupportLambda(name), name);
        }
        for (String name : List.of("md380e25$lambda$other$3$0", "md380e25$helper", "lambda$compute$",
                "md$lambda$compute$1$0", "mdZZ$lambda$compute$1$0", "xmd380e25$lambda$compute$1$0",
                "md380e25$lambda$compute$1$0$extra", "getProbability$3$0")) {
            assertFalse(VaultLootCdfLayouts.isVhapiSupportLambda(name), name);
        }
        try (var archive = VaultLootCdfProductionDifferentialTest.originalArchive(
                VaultLootCdfProductionDifferentialTest.fixture().get("jarSha256").getAsString())) {
            byte[] bytes = archive.bytes(CDF);
            Path jar = vhapiJars().get(0);
            ClassNode accepted = VaultLootCdfLazyTestHost.build(bytes, false, jar, true);
            addMerged(accepted, "md380e25$lambda$getProbability$3$0", VaultLootCdfLayouts.VHAPI_MIXIN);
            assertEquals(List.of(), VaultLootCdfLayouts.lazyApplicationProblems(accepted));

            ClassNode foreignOwner = VaultLootCdfLazyTestHost.build(bytes, false, jar, true);
            addMerged(foreignOwner, "md380e25$lambda$getProbability$3$0", "some.other.Mixin");
            assertTrue(VaultLootCdfLayouts.lazyApplicationProblems(foreignOwner).stream()
                    .anyMatch(problem -> problem.contains("unreviewed merged method owner")));

            ClassNode oddName = VaultLootCdfLazyTestHost.build(bytes, false, jar, true);
            addMerged(oddName, "md380e25$lambda$somethingElse$3$0", VaultLootCdfLayouts.VHAPI_MIXIN);
            assertTrue(VaultLootCdfLayouts.lazyApplicationProblems(oddName).stream()
                    .anyMatch(problem -> problem.contains("unreviewed member")));
        }
    }

    private static void addMerged(ClassNode node, String name, String owner) {
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                name, "()V", null, null);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        AnnotationNode annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        annotation.values = List.of("mixin", owner);
        method.visibleAnnotations = new ArrayList<>(List.of(annotation));
        node.methods.add(method);
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static void mutate(ClassNode node, int mutation) {
        switch (mutation) {
            case 0 -> { // another mixin merged a method
                MethodNode extra = new MethodNode(Opcodes.ACC_PRIVATE, "test$foreign", "()V", null, null);
                extra.instructions.add(new InsnNode(Opcodes.RETURN));
                extra.visibleAnnotations = new ArrayList<>(List.of(merged("example.ForeignCdfMixin")));
                node.methods.add(extra);
            }
            case 1 -> merged(method(node, "compute"), "example.ForeignKernelMixin");
            case 2 -> merged(method(node, "getProbability"), "example.ForeignKernelMixin");
            case 3 -> merged(method(node, "compute"), VaultLootCdfLayouts.MIXIN); // eager must not overwrite
            case 4 -> { // VHAPI merging something it does not merge
                MethodNode extra = new MethodNode(Opcodes.ACC_PRIVATE, "evil", "()V", null, null);
                extra.instructions.add(new InsnNode(Opcodes.RETURN));
                extra.visibleAnnotations = new ArrayList<>(List.of(merged(VaultLootCdfLayouts.VHAPI_MIXIN)));
                node.methods.add(extra);
            }
            case 5 -> { // the constructor redirect is gone
                MethodNode constructor = method(node, "<init>");
                MethodInsnNode handler = (MethodInsnNode) Arrays.stream(constructor.instructions.toArray())
                        .filter(i -> i instanceof MethodInsnNode call && call.name.contains("vhaccelerator$deferCompute"))
                        .findFirst().orElseThrow();
                constructor.instructions.set(handler, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CDF, "compute",
                        "()" + VaultLootCdfLayouts.MAP, false));
            }
            case 6 -> method(node, "<init>").instructions.insert(new InsnNode(Opcodes.NOP)); // constructor body changed
            case 7 -> { // a second compute call
                MethodNode constructor = method(node, "<init>");
                AbstractInsnNode last = constructor.instructions.getLast();
                while (last.getOpcode() != Opcodes.RETURN) last = last.getPrevious();
                constructor.instructions.insertBefore(last, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CDF, "compute",
                        "()" + VaultLootCdfLayouts.MAP, false));
                constructor.instructions.insertBefore(last, new InsnNode(Opcodes.POP));
            }
            case 8 -> { // get's cancellable flag changed
                MethodNode get = method(node, "get");
                AbstractInsnNode flag = Arrays.stream(get.instructions.toArray())
                        .filter(i -> i.getOpcode() == Opcodes.ICONST_1).findFirst().orElseThrow();
                get.instructions.set(flag, new InsnNode(Opcodes.ICONST_0));
            }
            case 9 -> { // get's original body changed after the hook
                MethodNode get = method(node, "get");
                FieldInsnNode field = (FieldInsnNode) Arrays.stream(get.instructions.toArray())
                        .filter(i -> i instanceof FieldInsnNode f && f.name.equals("map")).findFirst().orElseThrow();
                field.name = "samples";
                field.desc = "I";
            }
            case 10 -> { // getMap's hook replaced by a foreign callee
                MethodNode getMap = method(node, "getMap");
                MethodInsnNode handler = (MethodInsnNode) Arrays.stream(getMap.instructions.toArray())
                        .filter(i -> i instanceof MethodInsnNode call && call.name.contains("vhaccelerator$getMapLazily"))
                        .findFirst().orElseThrow();
                handler.name = "foreignHandler";
            }
            case 11 -> method(node, "getMap").instructions.insert(new InsnNode(Opcodes.NOP));
            case 12 -> node.fields.stream().filter(f -> f.name.equals("packBits")).findFirst().orElseThrow().desc = "J";
            case 13 -> node.methods.removeIf(m -> m.name.equals("get"));
            default -> throw new AssertionError(mutation);
        }
    }

    private static void merged(MethodNode method, String owner) {
        method.visibleAnnotations = new ArrayList<>(List.of(merged(owner)));
    }

    private static AnnotationNode merged(String owner) {
        AnnotationNode annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        annotation.values = List.of("mixin", owner);
        return annotation;
    }

    static List<double[]> vectors(JsonObject fixture) {
        List<double[]> vectors = new ArrayList<>();
        fixture.getAsJsonArray("vectors").forEach(element -> {
            double[] weights = new double[4];
            for (int i = 0; i < 4; i++) weights[i] = Double.longBitsToDouble(Long.parseUnsignedLong(
                    element.getAsJsonObject().getAsJsonArray("weightBits").get(i).getAsString(), 16));
            vectors.add(weights);
        });
        return vectors;
    }

    static void assertRaw(double expected, double actual, String context) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual), context);
    }

    /** Same keys, same values bit for bit, same default; iteration may differ in order. */
    static void assertViewEqual(Long2DoubleMap view, Long2DoubleMap expected) {
        assertEquals(expected.size(), view.size());
        assertRaw(expected.defaultReturnValue(), view.defaultReturnValue(), "default return value");
        for (var entry : expected.long2DoubleEntrySet()) {
            assertTrue(view.containsKey(entry.getLongKey()));
            assertRaw(entry.getDoubleValue(), view.get(entry.getLongKey()), "view value");
        }
        int seen = 0;
        for (var entry : view.long2DoubleEntrySet()) {
            seen++;
            assertTrue(expected.containsKey(entry.getLongKey()), "view key present in original");
            assertRaw(expected.get(entry.getLongKey()), entry.getDoubleValue(), "view entry value");
        }
        assertEquals(expected.size(), seen);
        assertEquals(expected.size(), view.keySet().size());
        assertEquals(expected.size(), view.values().size());
        assertTrue(view.equals(expected));
        assertTrue(expected.equals(view));
        assertEquals(expected.hashCode(), view.hashCode());
        assertFalse(view.containsKey(Long.MIN_VALUE));
        assertRaw(expected.get(Long.MIN_VALUE), view.get(Long.MIN_VALUE), "absent key");
    }
}
