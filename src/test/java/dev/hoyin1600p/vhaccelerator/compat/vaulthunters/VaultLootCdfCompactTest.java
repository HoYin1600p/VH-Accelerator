package dev.hoyin1600p.vhaccelerator.compat.vaulthunters;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import java.util.Random;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class VaultLootCdfCompactTest {
    private static void compositions(int total, int parts, Consumer<int[]> action) {
        permute(0, total, 0, new int[parts], action);
    }

    private static void permute(int sum, int total, int depth, int[] stack, Consumer<int[]> action) {
        if (depth == stack.length) { action.accept(stack); return; }
        if (depth == stack.length - 1) {
            stack[depth] = total - sum;
            permute(total, total, depth + 1, stack, action);
            return;
        }
        for (int i = 0; i <= total - sum; i++) {
            stack[depth] = i;
            permute(sum + i, total, depth + 1, stack, action);
        }
    }

    private static long pack(int[] frequencies, int packBits) {
        long v = 0;
        for (int frequency : frequencies) { v <<= packBits; v |= (long) frequency; }
        return v;
    }

    private static Long2DoubleOpenHashMap randomMap(int samples, int parts, long seed) {
        Random random = new Random(seed);
        int packBits = 64 / parts;
        Long2DoubleOpenHashMap map = new Long2DoubleOpenHashMap((int) VaultLootCdfCompact.count(samples, parts));
        compositions(samples, parts, frequencies ->
                map.put(pack(frequencies, packBits), Double.longBitsToDouble(random.nextLong())));
        return map;
    }

    @Test
    void rankIsABijectionOntoZeroToCountAndKeyAtInvertsIt() {
        for (int parts = 2; parts <= 8; parts++) {
            for (int samples : new int[]{0, 1, 2, 7, parts == 2 ? 255 : 12}) {
                int packBits = 64 / parts;
                Long2DoubleOpenHashMap map = randomMap(samples, parts, parts * 1000L + samples);
                VaultLootCdfCompact compact = VaultLootCdfCompact.tryCompact(map, samples, parts, packBits);
                assertNotNull(compact, parts + " parts, " + samples + " rolls");
                long count = VaultLootCdfCompact.count(samples, parts);
                boolean[] seen = new boolean[(int) count];
                compositions(samples, parts, frequencies -> {
                    long packed = pack(frequencies, packBits);
                    long rank = compact.rank(packed);
                    assertTrue(rank >= 0 && rank < count);
                    assertFalse(seen[(int) rank], "rank must be unique");
                    seen[(int) rank] = true;
                    assertEquals(packed, compact.keyAt(rank));
                });
                for (boolean flag : seen) assertTrue(flag);
            }
        }
    }

    @Test
    void thirtyThousandOutcomesReadBackBitForBitIncludingNanAndNegativeZero() {
        Long2DoubleOpenHashMap map = randomMap(53, 4, 7);
        map.put(map.keySet().iterator().nextLong(), Double.NaN);
        long otherKey = map.keySet().toLongArray()[5];
        map.put(otherKey, -0.0);
        VaultLootCdfCompact compact = VaultLootCdfCompact.tryCompact(map, 53, 4, 16);
        assertNotNull(compact);
        assertEquals(27_720, map.size());
        assertEquals(16 + 8 * 27_720L, compact.arrayBytes());
        for (var entry : map.long2DoubleEntrySet()) {
            assertEquals(Double.doubleToRawLongBits(entry.getDoubleValue()),
                    Double.doubleToRawLongBits(compact.get(entry.getLongKey())));
        }
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(compact.get(otherKey)));
        Long2DoubleMap view = compact.map();
        assertEquals(27_720, view.size());
        assertTrue(view.equals(map));
        assertTrue(map.equals(view));
    }

    @Test
    void absentKeysReadTheMapsDefaultValueLikeTheHashMap() {
        Long2DoubleOpenHashMap map = randomMap(10, 4, 1);
        map.defaultReturnValue(-3.5);
        VaultLootCdfCompact compact = VaultLootCdfCompact.tryCompact(map, 10, 4, 16);
        assertNotNull(compact);
        long wrongSum = pack(new int[]{10, 1, 0, 0}, 16);
        long negative = pack(new int[]{-1, 11, 0, 0}, 16);
        long highBits = pack(new int[]{10, 0, 0, 0}, 16) | (1L << 63) >>> 1;
        for (long absent : new long[]{0L, wrongSum, negative, highBits, Long.MIN_VALUE, -1L}) {
            assertFalse(map.containsKey(absent));
            assertEquals(-3.5, compact.get(absent));
            assertEquals(map.get(absent), compact.get(absent));
            assertFalse(compact.map().containsKey(absent));
        }
        assertEquals(-3.5, compact.map().defaultReturnValue());
    }

    @Test
    void anyDeviationFromTheCompleteCompositionSetKeepsTheOriginalMap() {
        int packBits = 16;
        // Missing entry.
        Long2DoubleOpenHashMap missing = randomMap(8, 4, 2);
        missing.remove(missing.keySet().iterator().nextLong());
        assertNull(VaultLootCdfCompact.tryCompact(missing, 8, 4, packBits));
        // Same size, but one key is not a composition of 8.
        Long2DoubleOpenHashMap foreign = randomMap(8, 4, 3);
        long removed = foreign.keySet().iterator().nextLong();
        foreign.remove(removed);
        foreign.put(pack(new int[]{9, 0, 0, 0}, packBits), 1.0);
        assertNull(VaultLootCdfCompact.tryCompact(foreign, 8, 4, packBits));
        // Same size, key with a bit outside every field.
        Long2DoubleOpenHashMap extraBits = randomMap(8, 3, 4);
        long first = extraBits.keySet().iterator().nextLong();
        extraBits.remove(first);
        extraBits.put(first | (1L << 63), 1.0);
        assertNull(VaultLootCdfCompact.tryCompact(extraBits, 8, 3, 21));
        // Extra entry.
        Long2DoubleOpenHashMap extra = randomMap(8, 4, 5);
        extra.put(pack(new int[]{8, 8, 8, 8}, packBits), 1.0);
        assertNull(VaultLootCdfCompact.tryCompact(extra, 8, 4, packBits));
        // Wrong declared shape for the keys.
        assertNull(VaultLootCdfCompact.tryCompact(randomMap(8, 4, 6), 7, 4, packBits));
        assertNull(VaultLootCdfCompact.tryCompact(randomMap(8, 4, 6), 8, 5, 12));
        assertNull(VaultLootCdfCompact.tryCompact(null, 8, 4, packBits));
    }

    @Test
    void unsupportedShapesAreRefusedUpFront() {
        assertTrue(VaultLootCdfCompact.supports(53, 4, 16));
        assertTrue(VaultLootCdfCompact.supports(30, 5, 12));
        assertTrue(VaultLootCdfCompact.supports(0, 2, 32));
        assertFalse(VaultLootCdfCompact.supports(5, 1, 64));
        assertFalse(VaultLootCdfCompact.supports(5, 9, 7));
        assertFalse(VaultLootCdfCompact.supports(-1, 4, 16));
        assertFalse(VaultLootCdfCompact.supports(256, 2, 32));
        assertFalse(VaultLootCdfCompact.supports(53, 4, 15), "pack width must be Vault's 64 / parts");
        assertFalse(VaultLootCdfCompact.supports(255, 8, 8), "255 rolls into eight pools exceeds the entry limit");
        assertFalse(VaultLootCdfCompact.supports(300, 4, 16));
        assertEquals(27_720, VaultLootCdfCompact.count(53, 4));
        long total = 0;
        for (int n = 1; n <= 53; n++) total += VaultLootCdfCompact.count(n, 4);
        assertEquals(395_009, total);
    }

    @Test
    void resolveCompactsWhenProvableAndOtherwiseReturnsTheComputedMapItself() {
        Long2DoubleOpenHashMap map = randomMap(12, 4, 9);
        VaultLootCdfDistribution compact = VaultLootCdfLazy.resolve(map, 12, 4, 16, 1_000);
        assertTrue(compact.compact());
        for (var entry : map.long2DoubleEntrySet()) {
            assertEquals(Double.doubleToRawLongBits(entry.getDoubleValue()),
                    Double.doubleToRawLongBits(compact.get(entry.getLongKey())));
        }
        Long2DoubleOpenHashMap broken = randomMap(12, 4, 10);
        broken.remove(broken.keySet().iterator().nextLong());
        VaultLootCdfDistribution kept = VaultLootCdfLazy.resolve(broken, 12, 4, 16, 1_000);
        assertFalse(kept.compact());
        assertSame(broken, kept.map());
        Long2DoubleOpenHashMap wide = randomMap(4, 9, 11);
        VaultLootCdfDistribution unsupported = VaultLootCdfLazy.resolve(wide, 4, 9, 7, 0);
        assertFalse(unsupported.compact());
        assertSame(wide, unsupported.map());
        assertNull(VaultLootCdfLazy.resolve(null, 12, 4, 16, 0), "a null computed map keeps the original null behaviour");
        long[] statistics = VaultLootCdfLazy.statistics();
        assertTrue(statistics[0] >= 3 && statistics[1] >= 1 && statistics[2] >= 2);
    }

    @Test
    void sharedCompactDistributionIsSafeForConcurrentReaders() throws Exception {
        Long2DoubleOpenHashMap map = randomMap(53, 4, 12);
        VaultLootCdfCompact compact = VaultLootCdfCompact.tryCompact(map, 53, 4, 16);
        assertNotNull(compact);
        long[] keys = map.keySet().toLongArray();
        Thread[] threads = new Thread[8];
        boolean[] ok = new boolean[threads.length];
        for (int t = 0; t < threads.length; t++) {
            int index = t;
            threads[t] = new Thread(() -> {
                boolean fine = true;
                for (int i = index; i < keys.length; i += threads.length) {
                    fine &= Double.doubleToRawLongBits(compact.get(keys[i])) == Double.doubleToRawLongBits(map.get(keys[i]));
                }
                ok[index] = fine;
            });
            threads[t].start();
        }
        for (Thread thread : threads) thread.join();
        for (boolean fine : ok) assertTrue(fine);
    }
}
