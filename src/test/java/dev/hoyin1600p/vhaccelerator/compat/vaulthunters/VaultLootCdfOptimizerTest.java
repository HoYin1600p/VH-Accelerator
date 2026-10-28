package dev.hoyin1600p.vhaccelerator.compat.vaulthunters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unimi.dsi.fastutil.doubles.Double2ObjectArrayMap;
import it.unimi.dsi.fastutil.doubles.Double2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

class VaultLootCdfOptimizerTest {
    private static final double[] SCORES = {
            1.0D,
            2.75D,
            5.5D,
            11.0D
    };

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 4, 8, 12})
    void matchesVaultArrayBucketOrdering(int samples) {
        Long2DoubleMap expected = referenceCompute(samples);
        Long2DoubleMap actual = VaultLootCdfOptimizer.compute(
                samples,
                SCORES.length,
                VaultLootCdfOptimizerTest::heuristic,
                VaultLootCdfOptimizerTest::pack,
                VaultLootCdfOptimizerTest::unpack,
                VaultLootCdfOptimizerTest::probability
        );

        assertEquals(expected.size(), actual.size());
        expected.long2DoubleEntrySet().forEach(entry ->
                assertEquals(
                        Double.doubleToLongBits(entry.getDoubleValue()),
                        Double.doubleToLongBits(
                                actual.get(entry.getLongKey())
                        ),
                        "cumulative probability for " + entry.getLongKey()
                )
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 4, 8, 12, 53})
    void predictsCompositionCount(int samples) {
        int expected = (samples + 1)
                * (samples + 2)
                * (samples + 3)
                / 6;
        assertEquals(
                expected,
                VaultLootCdfOptimizer.compositionCount(
                        samples,
                        SCORES.length
                )
        );
    }

    @Test
    void canonicalNanPayloadsAndSignedZeroKeepOriginalBucketsAndEntryOrder() {
        ToDoubleFunction<int[]> scores = frequencies -> frequencies[0] > 0
                ? Double.longBitsToDouble(0x7ff8000000000001L + frequencies[1])
                : frequencies[2] > 0 ? -0.0D : 0.0D;
        Long2DoubleMap expected = referenceCompute(4, scores);
        Long2DoubleMap actual = VaultLootCdfOptimizer.compute(4, SCORES.length, scores,
                VaultLootCdfOptimizerTest::pack, VaultLootCdfOptimizerTest::unpack,
                VaultLootCdfOptimizerTest::probability);
        assertEquals(expected.size(), actual.size());
        var left = expected.long2DoubleEntrySet().iterator();
        var right = actual.long2DoubleEntrySet().iterator();
        while (left.hasNext()) {
            var original = left.next();
            var optimized = right.next();
            assertEquals(original.getLongKey(), optimized.getLongKey());
            assertEquals(Double.doubleToRawLongBits(original.getDoubleValue()),
                    Double.doubleToRawLongBits(optimized.getDoubleValue()));
        }
    }

    @Test
    void neitherCdfMixinAddsInitializationToTheGuardedVaultClass() throws Exception {
        for (String mixin : List.of("TieredLootTableGeneratorCdfMixin", "cdf/TieredLootTableGeneratorEagerCdfMixin")) {
            String resource = "/dev/hoyin1600p/vhaccelerator/mixin/compat/vaulthunters/" + mixin + ".class";
            try (var input = getClass().getResourceAsStream(resource)) {
                org.junit.jupiter.api.Assertions.assertNotNull(input, resource);
                ClassNode node = new ClassNode();
                new ClassReader(input).accept(node, 0);
                org.junit.jupiter.api.Assertions.assertFalse(node.methods.stream()
                        .anyMatch(method -> method.name.equals("<clinit>")),
                        "A merged initializer changes the guarded CDF fingerprint: " + mixin);
            }
        }
    }

    private static Long2DoubleMap referenceCompute(int samples) {
        return referenceCompute(samples, VaultLootCdfOptimizerTest::heuristic);
    }

    private static Long2DoubleMap referenceCompute(int samples, ToDoubleFunction<int[]> heuristic) {
        Double2ObjectArrayMap<LongList> buckets =
                new Double2ObjectArrayMap<>();
        permute(
                0,
                samples,
                0,
                new int[SCORES.length],
                frequencies -> buckets.computeIfAbsent(
                        heuristic.applyAsDouble(frequencies),
                        ignored -> new LongArrayList()
                ).add(pack(frequencies))
        );

        List<Double2ObjectMap.Entry<LongList>> sorted =
                buckets.double2ObjectEntrySet()
                        .stream()
                        .sorted(Comparator.comparingDouble(
                                Double2ObjectMap.Entry::getDoubleKey
                        ))
                        .toList();
        Long2DoubleOpenHashMap result =
                new Long2DoubleOpenHashMap(sorted.size());
        double cumulative = 0.0D;
        for (Double2ObjectMap.Entry<LongList> entry : sorted) {
            LongList values = entry.getValue();
            for (int index = 0; index < values.size(); ++index) {
                long packed = values.getLong(index);
                cumulative += probability(unpack(packed));
                result.put(packed, cumulative);
            }
        }
        return result;
    }

    private static double heuristic(int[] frequencies) {
        double score = 0.0D;
        for (int index = 0; index < frequencies.length; ++index) {
            score -= SCORES[index] * frequencies[index];
        }
        return score;
    }

    private static double probability(int[] frequencies) {
        double probability = 1.0D;
        for (int index = 0; index < frequencies.length; ++index) {
            probability += frequencies[index] * (index + 1) / 10_000.0D;
        }
        return probability;
    }

    private static long pack(int[] frequencies) {
        long packed = 0L;
        for (int frequency : frequencies) {
            packed = packed << 16 | frequency;
        }
        return packed;
    }

    private static int[] unpack(long packed) {
        int[] frequencies = new int[SCORES.length];
        for (int index = frequencies.length - 1; index >= 0; --index) {
            frequencies[index] = (int) (packed & 0xFFFFL);
            packed >>>= 16;
        }
        return frequencies;
    }

    private static void permute(
            int sum,
            int total,
            int depth,
            int[] frequencies,
            Consumer<int[]> action
    ) {
        if (depth == frequencies.length) {
            action.accept(frequencies);
            return;
        }
        if (depth == frequencies.length - 1) {
            frequencies[depth] = total - sum;
            permute(total, total, depth + 1, frequencies, action);
            return;
        }
        for (int value = 0; value <= total - sum; ++value) {
            frequencies[depth] = value;
            permute(
                    sum + value,
                    total,
                    depth + 1,
                    frequencies,
                    action
            );
        }
    }
}
