package dev.hoyin1600p.vhaccelerator.compat.worldzones;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CompactChunkMembershipMapTest {
    static IntOpenHashSet set(int... ids) {
        IntOpenHashSet result = new IntOpenHashSet();
        for (int id : ids) result.add(id);
        return result;
    }
    static void equal(Long2ObjectMap<IntSet> a, Long2ObjectMap<IntSet> b, long key) {
        assertEquals(a.size(), b.size());
        assertEquals(a.containsKey(key), b.containsKey(key));
        if (!a.containsKey(key)) { assertNull(b.get(key)); return; }
        assertArrayEquals(a.get(key).toIntArray(), b.get(key).toIntArray(), "exact iterator order at " + key);
    }
    static void add(Long2ObjectMap<IntSet> map, long key, int id) {
        map.computeIfAbsent(key, ignored -> new IntOpenHashSet()).add(id);
    }
    static void remove(Long2ObjectMap<IntSet> map, long key, int id) {
        IntSet set = map.get(key);
        if (set != null) { set.remove(id); if (set.isEmpty()) map.remove(key); }
    }

    @Test void conversionPreservesZeroNegativeExtremeIdsAndOriginalFallbackIdentity() {
        var source = new Long2ObjectOpenHashMap<IntSet>();
        int[] ids = {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1, 31, 32};
        for (int i = 0; i < ids.length; i++) source.put((long) i - 3, set(ids[i]));
        IntSet multiple = set(0, -1, Integer.MAX_VALUE);
        source.put(Long.MIN_VALUE, multiple);
        var compact = CompactChunkMembershipMap.copyOf(source);
        assertNotNull(compact); assertEquals(ids.length, compact.inlineCount());
        assertSame(multiple, compact.get(Long.MIN_VALUE));
        for (long key = -3; key < 4; key++) equal(source, compact, key);
        equal(source, compact, Long.MIN_VALUE); equal(source, compact, Long.MAX_VALUE);
        assertEquals(IntOpenHashSet.class, source.get(0L).getClass(), "conversion does not replace original values");
    }

    @Test void expansionCollisionsRehashAndShrinkKeepOriginalIterationHistory() {
        for (int first : new int[]{0, -1, 1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            var expected = new Long2ObjectOpenHashMap<IntSet>(); expected.put(0L, set(first));
            var actual = CompactChunkMembershipMap.copyOf(expected);
            IntSet oracle = set(first); expected.put(0L, oracle);
            for (int i = -300; i < 301; i++) {
                add(expected, 0, i); add(actual, 0, i); equal(expected, actual, 0);
            }
            IntSet expanded = actual.get(0L); assertEquals(IntOpenHashSet.class, expanded.getClass());
            for (int i = -300; i < 301; i++) if (i != first) {
                remove(expected, 0, i); remove(actual, 0, i); equal(expected, actual, 0);
            }
            assertSame(expanded, actual.get(0L), "no repacking after multi-member history");
            for (int i = 700; i < 750; i++) { add(expected, 0, i); add(actual, 0, i); equal(expected, actual, 0); }
        }
    }

    @Test void duplicateAndAbsentRemovalDoNotCreateOrDestroyMemberships() {
        var expected = new Long2ObjectOpenHashMap<IntSet>(); expected.put(-1L, set(0));
        var actual = CompactChunkMembershipMap.copyOf(expected);
        expected.put(-1L, set(0));
        add(expected, -1, 0); add(actual, -1, 0); equal(expected, actual, -1);
        assertEquals(1, actual.inlineCount());
        remove(expected, -1, 9); remove(actual, -1, 9); equal(expected, actual, -1);
        remove(expected, -1, 0); remove(actual, -1, 0); equal(expected, actual, -1);
        add(expected, -1, 5); add(actual, -1, 5); equal(expected, actual, -1);
        assertEquals(IntOpenHashSet.class, actual.get(-1L).getClass());
        expected.clear(); actual.clear(); equal(expected, actual, -1);
    }

    @Test void onlyReviewedDefaultSingletonStateIsPacked() {
        var source = new Long2ObjectOpenHashMap<IntSet>();
        IntSet small = new IntOpenHashSet(1); small.add(7);
        IntSet otherLoad = new IntOpenHashSet(16, 0.5F); otherLoad.add(8);
        IntSet subclass = new IntOpenHashSet() { }; subclass.add(9);
        source.put(1L, small); source.put(2L, otherLoad); source.put(3L, subclass); source.put(4L, set());
        var compact = CompactChunkMembershipMap.copyOf(source); assertNotNull(compact);
        assertEquals(0, compact.inlineCount());
        assertSame(small, compact.get(1L)); assertSame(otherLoad, compact.get(2L));
        assertSame(subclass, compact.get(3L)); assertSame(source.get(4L), compact.get(4L));
        source.defaultReturnValue(set(99)); assertNull(CompactChunkMembershipMap.copyOf(source));
        assertNull(CompactChunkMembershipMap.copyOf(new Long2ObjectOpenHashMap<IntSet>() { }));
    }

    @Test void newMembershipFactoryIdentityAndRepeatedClearReloadStayOriginal() {
        var source = new Long2ObjectOpenHashMap<IntSet>(); source.put(2L, set(4));
        var compact = CompactChunkMembershipMap.copyOf(source);
        for (int pass = 0; pass < 3; pass++) {
            compact.clear(); IntSet created = set();
            assertSame(created, compact.computeIfAbsent(0L, key -> created));
            created.add(0); assertSame(created, compact.get(0L));
            assertSame(created, compact.computeIfAbsent(0L, key -> { throw new AssertionError(); }));
            compact = CompactChunkMembershipMap.copyOf(compact);
            assertNotNull(compact); assertEquals(1, compact.inlineCount());
            assertArrayEquals(new int[]{0}, compact.get(0L).toIntArray());
        }
    }

    @Test void seededMutationHistoriesMatchActualFastutilSetsAfterEveryOperation() {
        for (int seed = 0; seed < 30; seed++) {
            var expected = new Long2ObjectOpenHashMap<IntSet>();
            for (long key = -10; key < 10; key++) expected.put(key, set((int) key));
            var actual = CompactChunkMembershipMap.copyOf(expected);
            for (long key = -10; key < 10; key++) expected.put(key, set((int) key));
            Random rng = new Random(seed);
            for (int step = 0; step < 4000; step++) {
                long key = rng.nextInt(41) - 20; int id = rng.nextInt(97) - 48;
                if (rng.nextBoolean()) { add(expected, key, id); add(actual, key, id); }
                else { remove(expected, key, id); remove(actual, key, id); }
                equal(expected, actual, key);
            }
        }
    }
}
