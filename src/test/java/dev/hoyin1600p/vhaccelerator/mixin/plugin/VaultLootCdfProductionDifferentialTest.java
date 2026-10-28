package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import net.minecraftforge.api.distmarker.Dist;
import org.objectweb.asm.tree.ClassNode;

/** Executes original Vault classes and the actual compiled injection handler, without a Sponge host. */
class VaultLootCdfProductionDifferentialTest {
    private static final String GENERATOR = "iskallia.vault.core.world.loot.generator.TieredLootTableGenerator";
    private static final String CDF_HASH = "d907314fe4d67f9c536b9450ec09b73d2626cbd44edddf407a6903bf518fd96c";
    private static final int SEEDS = 10_000;

    @Test
    void productionPolicyActivationAndUnsupportedInputsExecuteOriginalFallback() throws Throwable {
        try (VaultLootCdfTestHost host = new VaultLootCdfTestHost();
             OriginalArchive archive = originalArchive(fixture().get("jarSha256").getAsString());
             OriginalArchive candidate = new OriginalArchive(archive.jar, true)) {
            host.attach(candidate);
            record Scenario(String name, boolean master, boolean setting, boolean compare, Dist side,
                            boolean clientPermission, boolean confirmed, boolean foreign, String version,
                            int samples, double[] weights) { }
            double[] weights = {1, 2, 3, 4};
            for (Scenario scenario : List.of(
                    new Scenario("common master off", false, true, false, Dist.DEDICATED_SERVER, true, true, false, archive.version(), 4, weights),
                    new Scenario("setting off", true, false, false, Dist.DEDICATED_SERVER, true, true, false, archive.version(), 4, weights),
                    new Scenario("Compare Mode", true, true, true, Dist.DEDICATED_SERVER, true, true, false, archive.version(), 4, weights),
                    new Scenario("client permission veto", true, true, false, Dist.CLIENT, false, true, false, archive.version(), 4, weights),
                    new Scenario("unconfirmed application", true, true, false, Dist.DEDICATED_SERVER, true, false, false, archive.version(), 4, weights),
                    new Scenario("foreign merged owner", true, true, false, Dist.DEDICATED_SERVER, true, true, true, archive.version(), 4, weights),
                    new Scenario("old short version", true, true, false, Dist.DEDICATED_SERVER, true, true, false, "3.21.62", 4, weights),
                    new Scenario("future version", true, true, false, Dist.DEDICATED_SERVER, true, true, false, "1.18.2-3.21.63", 4, weights),
                    new Scenario("unsupported samples", true, true, false, Dist.DEDICATED_SERVER, true, true, false, archive.version(), 54, weights),
                    new Scenario("unsupported dimensions", true, true, false, Dist.DEDICATED_SERVER, true, true, false, archive.version(), 4, new double[]{1}))) {
                host.runtime(scenario.master(), scenario.setting(), scenario.compare(), scenario.side(), scenario.clientPermission());
                // Each application check sees a fresh tree: foreign ownership must not poison later cases.
                CompatMixinGroups group = host.publish(scenario.version(), archive::bytes,
                        VaultLootCdfTestHost.adapt(archive.bytes(GENERATOR.replace('.', '/') + "$CDF")),
                        scenario.confirmed(), scenario.foreign());
                long before = candidate.originalBodyEntries();
                assertMapIdentical(archive.oracle(scenario.samples(), scenario.weights()).map(),
                        candidate.oracle(scenario.samples(), scenario.weights()).map(), scenario.name());
                assertEquals(before + 1, candidate.originalBodyEntries(), scenario.name());
                assertEquals(0, VaultLootCdfTestHost.uses(group), scenario.name());
            }
            host.runtime(true, true, false, Dist.DEDICATED_SERVER, true);
            CompatMixinGroups replaced = host.publish(archive.version(), name -> {
                byte[] content = archive.bytes(name);
                if (name.equals(GENERATOR.replace('.', '/') + "$CDF")) content[content.length - 1] ^= 1;
                return content;
            }, candidate.applicationTarget, true, false);
            long rejectedBefore = candidate.originalBodyEntries();
            assertMapIdentical(archive.oracle(4, weights).map(), candidate.oracle(4, weights).map(), "same-version replacement archive");
            assertEquals(rejectedBefore + 1, candidate.originalBodyEntries());
            assertEquals(0, VaultLootCdfTestHost.uses(replaced));
            host.runtime(true, true, false, Dist.CLIENT, true);
            CompatMixinGroups client = host.publish(archive.version(), archive::bytes,
                    candidate.applicationTarget, true, false);
            long before = candidate.originalBodyEntries();
            assertMapIdentical(archive.oracle(4, weights).map(), candidate.oracle(4, weights).map(), "client allowed");
            assertEquals(before, candidate.originalBodyEntries());
            assertEquals(1, VaultLootCdfTestHost.uses(client));
        }
    }

    @Test
    void pristineVaultStillFallsBackWhenVhapiIsPresentOrEitherKernelHasForeignOwnership() throws Throwable {
        try (VaultLootCdfTestHost host = new VaultLootCdfTestHost();
             OriginalArchive archive = originalArchive(fixture().get("jarSha256").getAsString());
             OriginalArchive candidate = new OriginalArchive(archive.jar, true)) {
            host.attach(candidate);
            double[] weights = {1, 2, 3, 4};
            for (Dist side : List.of(Dist.DEDICATED_SERVER, Dist.CLIENT)) {
                host.runtime(true, true, false, side, true);
                for (String kernel : List.of("vhapi", "compute", "getProbability")) {
                    CompatMixinGroups group = host.publish(archive.version(), archive::bytes,
                            VaultLootCdfTestHost.adapt(archive.bytes(GENERATOR.replace('.', '/') + "$CDF")),
                            true, false, kernel.equals("vhapi"), kernel.equals("vhapi") ? null : kernel);
                    long before = candidate.originalBodyEntries();
                    assertMapIdentical(archive.oracle(4, weights).map(), candidate.oracle(4, weights).map(),
                            side + ":" + kernel);
                    assertEquals(before + 1, candidate.originalBodyEntries(), "actual handler must preserve fallback");
                    assertFalse(CompatMixinGroups.activationAllowed(dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.MIXIN));
                    assertEquals(0, VaultLootCdfTestHost.uses(group));
                }
            }
        }
    }

    @Test
    void finalOwnershipRejectionAfterActivationRevokesTheActualHandlerWithoutEscaping() throws Throwable {
        try (VaultLootCdfTestHost host = new VaultLootCdfTestHost();
             OriginalArchive original = originalArchive(fixture().get("jarSha256").getAsString());
             OriginalArchive candidate = new OriginalArchive(original.jar, true)) {
            host.attach(candidate);
            CompatMixinGroups group = host.publish(original.version(), original::bytes, candidate.applicationTarget, true, false);
            assertTrue(CompatMixinGroups.activationAllowed(dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.MIXIN));
            assertDoesNotThrow(() -> group.applicationRejected(
                    dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.MIXIN,
                    dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.CDF.replace('/', '.'), List.of("late foreign kernel")));
            long before = candidate.originalBodyEntries();
            assertMapIdentical(original.oracle(4, new double[]{1, 2, 3, 4}).map(),
                    candidate.oracle(4, new double[]{1, 2, 3, 4}).map(), "late revocation");
            assertEquals(before + 1, candidate.originalBodyEntries());
            assertEquals(0, VaultLootCdfTestHost.uses(group));
        }
    }

    @Test
    void exactCompiledHeadPassesButChangedCallbackShapeOrCallBoundaryRejects() throws Exception {
        try (OriginalArchive original = originalArchive(fixture().get("jarSha256").getAsString())) {
            var layouts = dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.class;
            assertNotNull(layouts);
            assertTrue(dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.supports(original.version(), original::bytes));
            byte[] bytes = original.bytes(GENERATOR.replace('.', '/') + "$CDF");
            assertEquals(List.of(), dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.applicationProblems(
                    VaultLootCdfTestHost.adapt(bytes), false));
            for (int mutation = 0; mutation < 4; mutation++) {
                ClassNode target = VaultLootCdfTestHost.adapt(bytes);
                var compute = target.methods.stream().filter(method -> method.name.equals("compute")).findFirst().orElseThrow();
                if (mutation == 0) {
                    var flag = java.util.Arrays.stream(compute.instructions.toArray())
                            .filter(instruction -> instruction.getOpcode() == org.objectweb.asm.Opcodes.ICONST_1).findFirst().orElseThrow();
                    compute.instructions.set(flag, new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ICONST_0));
                } else {
                    var invoke = java.util.Arrays.stream(compute.instructions.toArray())
                            .filter(instruction -> instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                                    && call.name.equals("vhaccelerator$computeEagerly"))
                            .map(instruction -> (org.objectweb.asm.tree.MethodInsnNode) instruction).findFirst().orElseThrow();
                    if (mutation == 1) invoke.owner = "example/ForeignOwner";
                    else if (mutation == 2) invoke.setOpcode(org.objectweb.asm.Opcodes.INVOKEVIRTUAL);
                    else invoke.itf = true;
                }
                assertFalse(dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.applicationProblems(target, false).isEmpty());
            }
        }
    }

    @Test
    void everyStartupDistributionAndTenThousandSeededFrequencySequencesMatch() throws Throwable {
        JsonObject fixture = fixture();
        assertEquals(530, fixture.get("expectedUniqueConstructorKeys").getAsInt());
        assertEquals(67, fixture.get("supportedTableRefs").getAsInt());
        try (VaultLootCdfTestHost host = new VaultLootCdfTestHost();
             OriginalArchive archive = originalArchive(fixture.get("jarSha256").getAsString());
             OriginalArchive optimized = new OriginalArchive(archive.jar, true)) {
            host.attach(optimized);
            assertEquals("1.18.2-3.21.62", archive.version());
            CompatMixinGroups group = host.publish(archive.version(), archive::bytes, optimized.applicationTarget,
                    true, false);
            int constructors = 0;
            for (var element : fixture.getAsJsonArray("vectors")) {
                var vector = element.getAsJsonObject();
                double[] weights = new double[4];
                for (int i = 0; i < weights.length; i++) {
                    weights[i] = Double.longBitsToDouble(Long.parseUnsignedLong(
                            vector.getAsJsonArray("weightBits").get(i).getAsString(), 16));
                    assertEquals(weights[i], vector.getAsJsonArray("weights").get(i).getAsDouble());
                }
                Long2DoubleMap[] expected = new Long2DoubleMap[54];
                Long2DoubleMap[] actual = new Long2DoubleMap[54];
                Oracle[] oracles = new Oracle[54];
                for (int samples = 1; samples <= 53; samples++) {
                    Oracle oracle = archive.oracle(samples, weights);
                    oracles[samples] = oracle;
                    expected[samples] = oracle.map();
                    actual[samples] = optimized.oracle(samples, weights).map();
                    assertMapIdentical(expected[samples], actual[samples], vector.get("id") + ":" + samples);
                    constructors++;
                }
                // Reuse each seed's prefixes: every one of the 53 sample counts sees 10,000 seeds.
                Object weighted = archive.weightedList(weights);
                Object candidateWeighted = optimized.weightedList(weights);
                for (long seed = 0; seed < SEEDS; seed++) {
                    Random originalRandom = new Random(seed);
                    Random optimizedRandom = new Random(seed);
                    int[] originalFrequencies = new int[4];
                    int[] optimizedFrequencies = new int[4];
                    DoubleUnaryOperator originalSource = originalRandom::nextDouble;
                    DoubleUnaryOperator optimizedSource = optimizedRandom::nextDouble;
                    for (int samples = 1; samples <= 53; samples++) {
                        int originalPick = archive.pick(weighted, originalSource).orElseThrow();
                        int optimizedPick = optimized.pick(candidateWeighted, optimizedSource).orElseThrow();
                        assertEquals(originalPick, optimizedPick, "upstream weighted-list pick at seed " + seed);
                        originalFrequencies[originalPick]++;
                        optimizedFrequencies[optimizedPick]++;
                        assertRawEquals(oracles[samples].get(originalFrequencies),
                                optimized.get(samples, optimizedFrequencies), "seeded actual CDF.get");
                    }
                    assertEquals(originalRandom.nextLong(), optimizedRandom.nextLong(), "RNG state at seed " + seed);
                }
            }
            assertEquals(fixture.get("expectedUniqueConstructorKeys").getAsInt(), constructors);
            assertEquals(530, VaultLootCdfTestHost.uses(group), "each candidate constructor reached the production helper");
            assertEquals(530, archive.originalBodyEntries());
            assertEquals(0, optimized.originalBodyEntries(), "successful cancellation bypasses original compute");
        }
    }

    @Test
    void degenerateDistributionsRawKeyEqualityAndOriginalWeightedBoundariesMatch() throws Throwable {
        try (VaultLootCdfTestHost host = new VaultLootCdfTestHost();
             OriginalArchive archive = originalArchive(fixture().get("jarSha256").getAsString());
             OriginalArchive optimizedArchive = new OriginalArchive(archive.jar, true)) {
            host.attach(optimizedArchive);
            CompatMixinGroups group = host.publish(archive.version(), archive::bytes,
                    optimizedArchive.applicationTarget, true, false);
            for (double[] weights : List.of(new double[]{1, 1, 1, 1}, new double[]{0, 30, 20, 12},
                    new double[]{0, 0, 0, 0}, new double[]{-0.0, 1, 2, 3}, new double[]{-1, 1, 2, 3},
                    new double[]{Double.POSITIVE_INFINITY, 1, 2, 3},
                    new double[]{Double.longBitsToDouble(0x7ff8000000000001L), 1, 2, 3})) {
                for (int samples : new int[]{0, 1, 4}) {
                    Oracle first = archive.oracle(samples, weights);
                    Oracle reloaded = archive.oracle(samples, weights);
                    Long2DoubleMap before = first.map();
                    Oracle candidate = optimizedArchive.oracle(samples, weights);
                    Long2DoubleMap optimized = candidate.map();
                    assertMapIdentical(before, optimized, "degenerate " + Arrays.toString(weights));
                    assertMapIdentical(before, reloaded.map(), "repeated construction");
                    assertRawEquals(0.0, optimized.get(Long.MIN_VALUE), "missing key default");
                    if (Arrays.stream(weights).allMatch(Double::isFinite)) {
                        assertSeededResults(archive, optimizedArchive, first, candidate, weights, samples);
                    }
                }
            }
            // Raw weights are not normalized; array values, rather than array identity, define keys.
            Object key = archive.key(2, new double[]{1, 2, 3, 4});
            Object same = archive.key(2, new double[]{1, 2, 3, 4});
            assertEquals(key, same);
            assertEquals(key.hashCode(), same.hashCode());
            assertNotEquals(key, archive.key(2, new double[]{2, 4, 6, 8}));
            assertNotEquals(archive.key(2, new double[]{0, 1, 2, 3}),
                    archive.key(2, new double[]{-0.0, 1, 2, 3}));
            // With four dimensions a negative total has no compositions; one dimension reaches a negative factorial.
            assertTrue(archive.oracle(-1, new double[]{1, 1, 1, 1}).map().isEmpty());
            assertTrue(optimizedArchive.oracle(-1, new double[]{1, 1, 1, 1}).map().isEmpty());
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> archive.oracle(-1, new double[]{1}));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> optimizedArchive.oracle(-1, new double[]{1}));
            assertThrows(ArithmeticException.class, () -> archive.oracle(1, new double[]{}));
            assertThrows(ArithmeticException.class, () -> optimizedArchive.oracle(1, new double[]{}));
            assertEquals(21, VaultLootCdfTestHost.uses(group));
            assertEquals(2, optimizedArchive.originalBodyEntries(), "negative/out-of-domain inputs fall back; zero dimensions fails before compute");

            Object list = archive.weightedList(new double[]{1, 2, 0, 1});
            Object candidateList = optimizedArchive.weightedList(new double[]{1, 2, 0, 1});
            Oracle boundaryOriginal = archive.oracle(1, new double[]{1, 2, 0, 1});
            Oracle boundaryCandidate = optimizedArchive.oracle(1, new double[]{1, 2, 0, 1});
            for (DoubleUnaryOperator boundary : List.<DoubleUnaryOperator>of(bound -> 0,
                    bound -> Math.nextDown(1.0), bound -> 1.0, bound -> Math.nextDown(3.0),
                    bound -> 3.0, bound -> Math.nextDown(bound), bound -> bound)) {
                Optional<Integer> left = archive.pick(list, boundary);
                Optional<Integer> right = optimizedArchive.pick(candidateList, boundary);
                assertEquals(left, right);
                if (left.isPresent()) {
                    int[] frequencies = new int[4];
                    frequencies[left.get()] = 1;
                    assertRawEquals(boundaryOriginal.get(frequencies), boundaryCandidate.get(frequencies), "boundary CDF.get");
                }
            }
            assertEquals(Optional.of(0), archive.pick(list, bound -> 0));
            assertEquals(Optional.of(0), archive.pick(list, bound -> Math.nextDown(1.0)));
            assertEquals(Optional.of(1), archive.pick(list, bound -> 1.0));
            assertEquals(Optional.of(1), archive.pick(list, bound -> Math.nextDown(3.0)));
            assertEquals(Optional.of(3), archive.pick(list, bound -> 3.0));
            assertEquals(Optional.of(3), archive.pick(list, bound -> Math.nextDown(bound)));
            assertEquals(Optional.empty(), archive.pick(list, bound -> bound));
            int[] calls = {0};
            assertEquals(Optional.empty(), archive.pick(archive.weightedList(new double[]{0, 0, 0, 0}),
                    bound -> { calls[0]++; return 0; }));
            assertEquals(0, calls[0], "empty-weight selection consumes no randomness");
            assertEquals(Optional.empty(), optimizedArchive.pick(optimizedArchive.weightedList(new double[]{0, 0, 0, 0}),
                    bound -> { calls[0]++; return 0; }));
            assertEquals(0, calls[0], "candidate empty-weight selection consumes no randomness");
        }
    }

    private static void assertSeededResults(OriginalArchive originalArchive, OriginalArchive candidateArchive,
            Oracle original, Oracle candidate, double[] weights, int samples) throws Throwable {
        Object originalList = originalArchive.weightedList(weights);
        Object candidateList = candidateArchive.weightedList(weights);
        for (long seed = 0; seed < SEEDS; seed++) {
            Random left = new Random(seed);
            Random right = new Random(seed);
            int[] leftFrequencies = new int[weights.length];
            int[] rightFrequencies = new int[weights.length];
            for (int i = 0; i < samples; i++) {
                Optional<Integer> expected = originalArchive.pick(originalList, left::nextDouble);
                Optional<Integer> actual = candidateArchive.pick(candidateList, right::nextDouble);
                assertEquals(expected, actual);
                expected.ifPresent(index -> leftFrequencies[index]++);
                actual.ifPresent(index -> rightFrequencies[index]++);
            }
            assertArrayEquals(leftFrequencies, rightFrequencies);
            assertRawEquals(original.get(leftFrequencies), candidate.get(rightFrequencies), "degenerate seeded CDF.get");
            assertEquals(left.nextLong(), right.nextLong(), "degenerate RNG state");
        }
    }

    static JsonObject fixture() throws Exception {
        String external = System.getProperty("vault.cdf.fixture");
        try (InputStream input = external == null ? VaultLootCdfProductionDifferentialTest.class.getResourceAsStream(
                "/dev/hoyin1600p/vhaccelerator/compat/vaulthunters/startup-cdf-weight-fixture.json")
                : Files.newInputStream(Path.of(external))) {
            assertNotNull(input);
            JsonObject fixture = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            if (external != null) verifyCoverage(fixture, Path.of(external).resolveSibling("startup-cdf-source-manifest.json"));
            return fixture;
        }
    }

    private static void verifyCoverage(JsonObject fixture, Path manifest) throws Exception {
        JsonObject coverage;
        try (var reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            coverage = JsonParser.parseReader(reader).getAsJsonObject();
        }
        assertEquals(fixture.get("jarSha256"), coverage.get("jarSha256"));
        assertEquals(fixture.get("registrySha256"), coverage.get("registrySha256"));
        assertEquals(coverage.get("registeredTableRefs").getAsInt(), coverage.getAsJsonArray("tables").size());
        java.util.Set<String> vectorIds = new java.util.HashSet<>();
        for (var value : fixture.getAsJsonArray("vectors")) {
            var vector = value.getAsJsonObject();
            var raw = java.nio.ByteBuffer.allocate(32);
            for (var bit : vector.getAsJsonArray("weightBits")) raw.putLong(Long.parseUnsignedLong(bit.getAsString(), 16));
            assertEquals(vector.get("id").getAsString(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.array())));
            assertTrue(vectorIds.add(vector.get("id").getAsString()));
        }
        java.util.Set<String> referenced = new java.util.HashSet<>();
        int supported = 0, ordinal = 0;
        for (var value : coverage.getAsJsonArray("tables")) {
            var table = value.getAsJsonObject();
            assertEquals(ordinal++, table.get("ordinal").getAsInt(), "registry encounter order");
            if (table.get("supported").getAsBoolean()) {
                String id = table.get("vectorId").getAsString();
                assertTrue(vectorIds.contains(id), "each supported table must resolve to tested ordered raw weights");
                referenced.add(id); supported++;
            } else assertTrue(table.has("reason"), "unsupported references must be accounted for");
        }
        assertEquals(vectorIds, referenced);
        assertEquals(fixture.get("supportedTableRefs").getAsInt(), supported);
        assertEquals(coverage.get("supportedTableRefs").getAsInt(), supported);
        assertEquals(vectorIds.size() * 53, fixture.get("expectedUniqueConstructorKeys").getAsInt());
        assertEquals(1, fixture.getAsJsonArray("samplesInclusive").get(0).getAsInt());
        assertEquals(53, fixture.getAsJsonArray("samplesInclusive").get(1).getAsInt());
        assertEquals(0, fixture.getAsJsonArray("seedRangeInclusive").get(0).getAsInt());
        assertEquals(9999, fixture.getAsJsonArray("seedRangeInclusive").get(1).getAsInt());
    }

    static void assertMapIdentical(Long2DoubleMap expected, Long2DoubleMap actual, String context) {
        assertEquals(expected.size(), actual.size(), context);
        var left = expected.long2DoubleEntrySet().iterator();
        var right = actual.long2DoubleEntrySet().iterator();
        while (left.hasNext()) {
            var original = left.next();
            var optimized = right.next();
            assertEquals(original.getLongKey(), optimized.getLongKey(), "entry order: " + context);
            assertRawEquals(original.getDoubleValue(), optimized.getDoubleValue(), context);
        }
        assertFalse(right.hasNext(), context);
    }

    private static void assertRawEquals(double expected, double actual, String context) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual), context);
    }

    static OriginalArchive originalArchive(String expectedHash) throws Exception {
        String explicit = System.getProperty("vault.cdf.jar");
        if (explicit != null) {
            Path jar = Path.of(explicit);
            assertEquals(expectedHash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))),
                    "Explicit raw original archive must match the gate/corpus, never a same-version replacement");
            return new OriginalArchive(jar);
        }
        List<Path> candidates;
        try (var files = Files.list(Path.of("libs"))) {
            candidates = files.filter(path -> path.getFileName().toString().startsWith("the_vault-")
                    && path.toString().endsWith(".jar")).sorted().toList();
        }
        for (Path jar : candidates) {
            try (InputStream input = Files.newInputStream(jar)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[65536];
                for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
                if (HexFormat.of().formatHex(digest.digest()).equals(expectedHash)) return new OriginalArchive(jar);
            }
        }
        throw new AssertionError("The exact local Vault fixture archive is required under libs; expected SHA-256 " + expectedHash);
    }

    static final class OriginalArchive extends ClassLoader implements AutoCloseable {
        final Path jar;
        private final ZipFile zip;
        private final boolean adapted;
        final ClassNode applicationTarget;
        private final Map<Integer, Oracle> distributions = new java.util.HashMap<>();
        private final Constructor<?> keyConstructor;
        private final Constructor<?> cdfConstructor;
        private final MethodHandle weightedConstructor;
        private final MethodHandle weightedPick;

        OriginalArchive(Path jar) throws Exception { this(jar, false); }

        OriginalArchive(Path jar, boolean adapted) throws Exception {
            super(VaultLootCdfProductionDifferentialTest.class.getClassLoader());
            this.jar = jar;
            this.adapted = adapted;
            zip = new ZipFile(jar.toFile());
            byte[] cdfBytes;
            try (var input = zip.getInputStream(zip.getEntry(GENERATOR.replace('.', '/') + "$CDF.class"))) {
                cdfBytes = input.readAllBytes();
            }
            assertEquals(CDF_HASH, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cdfBytes)));
            applicationTarget = adapted ? VaultLootCdfTestHost.adapt(cdfBytes) : VaultLootCdfTestHost.read(cdfBytes);
            Class<?> keyType = loadClass(GENERATOR + "$CDFKey");
            keyConstructor = keyType.getConstructor(double[].class);
            cdfConstructor = loadClass(GENERATOR + "$CDF").getDeclaredConstructor(keyType);
            cdfConstructor.setAccessible(true);
            Class<?> weightedType = loadClass("iskallia.vault.core.util.WeightedList");
            var lookup = MethodHandles.publicLookup();
            weightedConstructor = lookup.findConstructor(weightedType, MethodType.methodType(void.class));
            // Method handles resolve only this Java-only descriptor, avoiding unrelated game overloads.
            weightedPick = lookup.findVirtual(weightedType, "getRandom",
                    MethodType.methodType(Optional.class, DoubleUnaryOperator.class));
        }

        Object key(int samples, double[] weights) throws Exception {
            double[] key = new double[weights.length + 1];
            key[0] = samples;
            System.arraycopy(weights, 0, key, 1, weights.length);
            return keyConstructor.newInstance((Object) key);
        }

        Oracle oracle(int samples, double[] weights) throws Throwable {
            try {
                Oracle oracle = new Oracle(cdfConstructor.newInstance(key(samples, weights)));
                distributions.put(samples, oracle);
                return oracle;
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }

        double get(int samples, int[] frequencies) { return distributions.get(samples).get(frequencies); }

        long originalBodyEntries() throws Exception {
            return loadClass(GENERATOR + "$CDF").getField(VaultLootCdfTestHost.BODY_ENTRIES).getLong(null);
        }

        byte[] bytes(String internalName) {
            try {
                var entry = zip.getEntry(internalName + ".class");
                if (entry == null) return null;
                try (var input = zip.getInputStream(entry)) { return input.readAllBytes(); }
            } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }

        String version() throws Exception {
            // Vault's manifest supplies the ${file.jarVersion} in mods.toml.
            try (var input = zip.getInputStream(zip.getEntry("META-INF/mods.toml"))) {
                String metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(metadata.contains("modId=\"the_vault\""));
                assertTrue(metadata.contains("version=\"${file.jarVersion}\""));
            }
            try (var input = zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF"))) {
                return new java.util.jar.Manifest(input).getMainAttributes().getValue("Implementation-Version");
            }
        }

        @SuppressWarnings("unchecked")
        Object weightedList(double[] weights) throws Throwable {
            Object list = weightedConstructor.invoke();
            Map<Object, Double> map = (Map<Object, Double>) list;
            for (int i = 0; i < weights.length; i++) map.put(i, weights[i]);
            return list;
        }

        @SuppressWarnings("unchecked")
        Optional<Integer> pick(Object list, DoubleUnaryOperator source) throws Throwable {
            return (Optional<Integer>) weightedPick.invoke(list, source);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            boolean sideBinding = adapted && name.equals("net.minecraftforge.fml.loading.FMLEnvironment");
            boolean productionPolicy = adapted && name.equals(
                    "dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultLootCdfPolicy");
            if (!name.startsWith("iskallia.vault.") && !sideBinding && !productionPolicy) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    if (sideBinding || productionPolicy) {
                        try {
                            byte[] content;
                            if (sideBinding) content = VaultLootCdfTestHost.sideBinding();
                            else try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                                assertNotNull(input);
                                content = input.readAllBytes();
                            }
                            loaded = defineClass(name, content, 0, content.length);
                        } catch (java.io.IOException failure) { throw new ClassNotFoundException(name, failure); }
                        if (resolve) resolveClass(loaded);
                        return loaded;
                    }
                    var entry = zip.getEntry(name.replace('.', '/') + ".class");
                    if (entry == null) throw new ClassNotFoundException(name);
                    try (var input = zip.getInputStream(entry)) {
                        byte[] bytes = input.readAllBytes();
                        if (name.equals(GENERATOR + "$CDF")) {
                            bytes = VaultLootCdfTestHost.executable(applicationTarget, adapted);
                        }
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }

        @Override public void close() throws Exception { zip.close(); }
    }

    static final class Oracle {
        private final Object original;
        private final Method getMap;
        private final Method get;

        Oracle(Object original) throws Exception {
            this.original = original;
            Class<?> type = original.getClass();
            getMap = type.getMethod("getMap");
            get = type.getMethod("get", int[].class);
        }

        Long2DoubleMap map() { return (Long2DoubleMap) call(getMap); }
        double get(int[] frequencies) { return (Double) call(get, frequencies); }

        private Object call(Method method, Object... arguments) {
            try {
                return method.invoke(original, arguments);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Original Vault oracle failed: " + method.getName(), failure);
            }
        }
    }
}
