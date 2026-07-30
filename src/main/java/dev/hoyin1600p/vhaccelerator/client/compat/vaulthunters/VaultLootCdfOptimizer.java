package dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters;

import it.unimi.dsi.fastutil.doubles.Double2ObjectMap;
import it.unimi.dsi.fastutil.doubles.Double2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Builds Vault's tiered-loot cumulative distribution without quadratic buckets.
 */
public final class VaultLootCdfOptimizer {
    private static final int MAX_INITIAL_CAPACITY = 1_000_000;

    private VaultLootCdfOptimizer() {
    }

    public static Long2DoubleMap compute(
            int samples,
            int dimensions,
            ToDoubleFunction<int[]> heuristic,
            ToLongFunction<int[]> pack,
            LongFunction<int[]> unpack,
            ToDoubleFunction<int[]> probability
    ) {
        int compositionCount = compositionCount(samples, dimensions);
        Double2ObjectOpenHashMap<LongList> buckets =
                new Double2ObjectOpenHashMap<>(compositionCount);

        permute(
                0,
                samples,
                0,
                new int[dimensions],
                frequencies -> buckets.computeIfAbsent(
                        heuristic.applyAsDouble(frequencies),
                        ignored -> new LongArrayList()
                ).add(pack.applyAsLong(frequencies))
        );

        List<Double2ObjectMap.Entry<LongList>> sorted =
                buckets.double2ObjectEntrySet()
                        .stream()
                        .sorted(Comparator.comparingDouble(
                                Double2ObjectMap.Entry::getDoubleKey
                        ))
                        .toList();
        Long2DoubleOpenHashMap result =
                new Long2DoubleOpenHashMap(compositionCount);
        double cumulative = 0.0D;

        for (Double2ObjectMap.Entry<LongList> entry : sorted) {
            LongList packedFrequencies = entry.getValue();
            for (int index = 0; index < packedFrequencies.size(); ++index) {
                long packed = packedFrequencies.getLong(index);
                cumulative += probability.applyAsDouble(
                        unpack.apply(packed)
                );
                result.put(packed, cumulative);
            }
        }
        return result;
    }

    static int compositionCount(int samples, int dimensions) {
        if (samples < 0 || dimensions <= 0) {
            throw new IllegalArgumentException(
                    "samples must be non-negative and dimensions positive"
            );
        }

        int choose = Math.min(dimensions - 1, samples);
        long total = 1L;
        long top = (long) samples + dimensions - 1L;
        for (int index = 1; index <= choose; ++index) {
            total = total * (top - choose + index) / index;
            if (total >= MAX_INITIAL_CAPACITY) {
                return MAX_INITIAL_CAPACITY;
            }
        }
        return Math.max(1, (int) total);
    }

    private static void permute(
            int sum,
            int total,
            int depth,
            int[] frequencies,
            java.util.function.Consumer<int[]> action
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
