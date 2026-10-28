package dev.hoyin1600p.vhaccelerator.compat.buildinggadgets;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

class CompactTemplateLookupTest {
    private static final int COLLIDING = 128;

    private static BlockPos collision(int i) { return new BlockPos(961 * i, 0, 63 - i); }

    private static ImmutableMap<BlockPos, Object> map() {
        ImmutableMap.Builder<BlockPos, Object> builder = ImmutableMap.builder();
        for (int i = 0; i < COLLIDING; i++) builder.put(collision(i), new String("value-" + i));
        builder.put(new BlockPos(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE), new String("min"));
        builder.put(new BlockPos(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE), new String("max"));
        ImmutableMap<BlockPos, Object> map = builder.build();
        assertEquals("com.google.common.collect.JdkBackedImmutableMap", map.getClass().getName());
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Map<BlockPos, Object> delegate(ImmutableMap<BlockPos, ?> map) throws Exception {
        Field field = map.getClass().getDeclaredField("delegateMap");
        assertTrue(field.trySetAccessible());
        return (Map<BlockPos, Object>) field.get(map);
    }

    @Test void preservesOuterMapCachedViewsEntriesKeysValuesAndEncounterOrder() throws Exception {
        ImmutableMap<BlockPos, Object> map = map();
        Object entrySet = map.entrySet(), keySet = map.keySet(), values = map.values();
        ImmutableList<Map.Entry<BlockPos, Object>> entries = map.entrySet().asList();
        ImmutableList<BlockPos> keys = map.keySet().asList();
        ImmutableList<Object> orderedValues = map.values().asList();
        Object multimap = map.asMultimap();
        List<Map.Entry<BlockPos, Object>> before = new ArrayList<>(entries);
        int oldHash = map.hashCode();
        assertTrue(CompactTemplateLookup.install(map));
        assertSame(entrySet, map.entrySet());
        assertSame(keySet, map.keySet());
        assertSame(values, map.values());
        assertSame(entries, map.entrySet().asList());
        assertSame(keys, map.keySet().asList());
        assertEquals(orderedValues, map.values().asList());
        assertSame(multimap, map.asMultimap());
        assertSame(map, ImmutableMap.copyOf(map));
        assertEquals(oldHash, map.hashCode());
        List<Object> visited = new ArrayList<>();
        map.forEach((key, value) -> visited.add(value));
        for (int i = 0; i < before.size(); i++) {
            Map.Entry<BlockPos, Object> old = before.get(i);
            assertSame(old, map.entrySet().asList().get(i));
            assertSame(old.getKey(), map.keySet().asList().get(i));
            assertSame(orderedValues.get(i), map.values().asList().get(i));
            assertSame(old.getValue(), visited.get(i));
            assertSame(old.getValue(), map.asMultimap().get(old.getKey()).iterator().next());
        }
        ImmutableMap<BlockPos, Object> collected = map.entrySet().stream().collect(
                ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
        assertEquals(keys, collected.keySet().asList());
        for (BlockPos key : keys) assertSame(map.get(key), collected.get(key));
    }

    @Test void matchesOriginalForNullAbsentCollisionsAndEntireIntegerCoordinateRange() throws Exception {
        ImmutableMap<BlockPos, Object> map = map();
        Map<BlockPos, Object> original = delegate(map);
        assertTrue(CompactTemplateLookup.install(map));
        for (BlockPos key : map.keySet()) {
            assertSame(original.get(key), map.get(new BlockPos(key.getX(), key.getY(), key.getZ())));
        }
        for (Object key : new Object[] {null, BlockPos.ZERO, new BlockPos(-1, -1, -1),
                new Vec3i(collision(4).getX(), 0, collision(4).getZ()),
                new BlockPos.MutableBlockPos(collision(7).getX(), 0, collision(7).getZ()),
                new BlockPos(collision(9).getX(), 0, collision(9).getZ()) { }}) {
            assertSame(original.get(key), map.get(key));
        }
    }

    @Test void arbitraryQueryPreservesOriginalHashAndTreeEqualityCallbackSequence() throws Exception {
        ImmutableMap<BlockPos, Object> map = map();
        Map<BlockPos, Object> original = delegate(map);
        OddQuery query = new OddQuery(collision(91), false, false);
        Object expected = original.get(query);
        List<String> callbacks = List.copyOf(query.callbacks);
        query.callbacks.clear();
        assertTrue(CompactTemplateLookup.install(map));
        assertSame(expected, map.get(query));
        assertEquals(callbacks, query.callbacks);
        assertEquals(1, query.callbacks.stream().filter("hash"::equals).count());
    }

    @Test void customVec3iAndBlockPosSubclassQueriesMatchOriginalWithoutPerCallRebuild() throws Exception {
        ImmutableMap<BlockPos, Object> map = map();
        Map<BlockPos, Object> original = delegate(map);
        assertTrue(CompactTemplateLookup.install(map));
        Map<BlockPos, Object> lookup = delegate(map);
        BlockPos target = collision(17);
        // Subclasses with their own equals direction: equal to the stored key, or never equal.
        Vec3i equalSub = new Vec3i(target.getX(), target.getY(), target.getZ()) {
            @Override public int hashCode() { return target.hashCode(); }
            @Override public boolean equals(Object other) { return other instanceof Vec3i v && v.getX() == getX()
                    && v.getY() == getY() && v.getZ() == getZ(); }
        };
        Vec3i neverEqual = new Vec3i(target.getX(), target.getY(), target.getZ()) {
            @Override public int hashCode() { return target.hashCode(); }
            @Override public boolean equals(Object other) { return false; }
        };
        BlockPos posSub = new BlockPos(target.getX(), target.getY(), target.getZ()) { };
        BlockPos absentSub = new BlockPos(-5, -6, -7) { };
        for (Object query : new Object[] {equalSub, neverEqual, posSub, absentSub}) {
            for (int call = 0; call < 3; call++) assertSame(original.get(query), map.get(query));
        }
        assertSame(original.get(equalSub), map.get(equalSub));
        assertNull(map.get(neverEqual));
        // Repeated unknown-class queries must not allocate a fresh table each time.
        Field reference = lookup.getClass().getDeclaredField("fallbackReference");
        assertTrue(reference.trySetAccessible());
        Object first = reference.get(lookup);
        assertNotNull(first);
        map.get(posSub);
        assertSame(first, reference.get(lookup));
    }

    @Test void standardMutableAndVec3iQueriesUseBoundedIndexRatherThanRebuildingTheDelegate() throws Exception {
        ImmutableMap<BlockPos, Object> map = map();
        Map<BlockPos, Object> original = delegate(map);
        assertTrue(CompactTemplateLookup.install(map));
        Map<BlockPos, Object> lookup = delegate(map);
        AtomicInteger reads = new AtomicInteger();
        ImmutableList.Builder<Map.Entry<BlockPos, Object>> instrumented = ImmutableList.builder();
        for (Map.Entry<BlockPos, Object> entry : map.entrySet()) {
            instrumented.add(new AbstractMap.SimpleImmutableEntry<>(entry) {
                @Override public BlockPos getKey() { reads.incrementAndGet(); return super.getKey(); }
            });
        }
        // Instrument entry access, not the query or stored keys: rebuilding reads every entry.
        Field entries = lookup.getClass().getDeclaredField("entries");
        assertTrue(entries.trySetAccessible());
        entries.set(lookup, instrumented.build());
        BlockPos target = collision(42);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(target.getX(), target.getY(), target.getZ());
        for (Object query : List.of(new Vec3i(target.getX(), target.getY(), target.getZ()), mutable)) {
            reads.set(0);
            assertSame(original.get(query), map.get(query));
            assertTrue(reads.get() < COLLIDING, "standard query must not reconstruct the whole map");
        }
        mutable.set(-1, -2, -3);
        reads.set(0);
        assertNull(map.get(mutable));
        assertTrue(reads.get() < COLLIDING);
        assertNull(map.get(new Vec3i(-1, -2, -3)));
        assertNull(map.get(null));
    }

    @Test void arbitraryQueryExceptionsHaveOriginalProvenanceAndCallbackCounts() throws Exception {
        ImmutableMap<BlockPos, Object> map = map();
        Map<BlockPos, Object> original = delegate(map);
        assertTrue(CompactTemplateLookup.install(map));
        for (OddQuery query : List.of(new OddQuery(collision(40), true, false), new OddQuery(collision(40), false, true))) {
            RuntimeException expected = assertThrows(RuntimeException.class, () -> original.get(query));
            List<String> callbacks = List.copyOf(query.callbacks);
            query.callbacks.clear();
            assertSame(expected, assertThrows(RuntimeException.class, () -> map.get(query)));
            assertEquals(callbacks, query.callbacks);
        }
    }

    @Test void keepsEqualButDistinctAndMutableValueIdentitiesWithoutHashingValues() {
        ImmutableMap.Builder<BlockPos, Object> builder = ImmutableMap.builder();
        List<MutableValue> values = new ArrayList<>();
        for (int i = 0; i < COLLIDING; i++) {
            MutableValue value = new MutableValue();
            values.add(value);
            builder.put(collision(i), value);
        }
        ImmutableMap<BlockPos, Object> map = builder.build();
        assertTrue(CompactTemplateLookup.install(map));
        assertNotSame(values.get(0), values.get(1));
        for (int i = 0; i < values.size(); i++) assertSame(values.get(i), map.get(collision(i)));
        values.get(0).state = 8;
        assertEquals(8, ((MutableValue) map.get(collision(0))).state);
    }

    @Test void replacementRetainsOnlyOriginalEntriesAndPrimitiveIndexNotOldDelegate() throws Exception {
        ImmutableMap<BlockPos, Object> map = map();
        Map<BlockPos, Object> old = delegate(map);
        assertTrue(CompactTemplateLookup.install(map));
        Map<BlockPos, Object> replacement = delegate(map);
        assertNotSame(old, replacement);
        boolean primitiveIndex = false;
        for (Field field : replacement.getClass().getDeclaredFields()) {
            assertTrue(field.trySetAccessible());
            assertNotSame(old, field.get(replacement));
            if (field.getType() == int[].class) primitiveIndex = true;
            assertFalse(Map.class.isAssignableFrom(field.getType()), "must not retain a lookup map");
        }
        assertTrue(primitiveIndex);
        assertFalse(CompactTemplateLookup.install(map));
        assertSame(replacement, delegate(map));
    }

    @Test void unsupportedMapsAndDeniedReflectionRemainUnchanged() throws Exception {
        assertFalse(CompactTemplateLookup.install(null));
        assertFalse(CompactTemplateLookup.install(ImmutableMap.of()));
        assertFalse(CompactTemplateLookup.install(ImmutableMap.of(BlockPos.ZERO, "value")));
        ImmutableMap<BlockPos, Object> map = map();
        Map<BlockPos, Object> original = delegate(map);
        assertFalse(CompactTemplateLookup.install(map, field -> false));
        assertSame(original, delegate(map));
        ImmutableMap.Builder<BlockPos, Object> mutable = ImmutableMap.builder();
        for (int i = 0; i < COLLIDING; i++) mutable.put(collision(i), "value");
        mutable.put(new BlockPos.MutableBlockPos(123, 456, 789), "mutable");
        ImmutableMap<BlockPos, Object> unsupported = mutable.build();
        Map<BlockPos, Object> unsupportedOriginal = delegate(unsupported);
        assertFalse(CompactTemplateLookup.install(unsupported));
        assertSame(unsupportedOriginal, delegate(unsupported));
    }

    @Test void unexpectedDelegateAndOtherMapInstancesAreNotModified() throws Exception {
        ImmutableMap<BlockPos, Object> first = map(), second = map();
        Map<BlockPos, Object> secondOriginal = delegate(second);
        assertTrue(CompactTemplateLookup.install(first));
        assertSame(secondOriginal, delegate(second));
        Field field = second.getClass().getDeclaredField("delegateMap");
        assertTrue(field.trySetAccessible());
        HashMap<BlockPos, Object> subclass = new HashMap<>(secondOriginal) { };
        field.set(second, subclass);
        assertFalse(CompactTemplateLookup.install(second));
        assertSame(subclass, delegate(second));
    }

    @Test void replacementAndAllViewsRejectMutationWithoutInvokingCallbacks() throws Exception {
        ImmutableMap<BlockPos, Object> map = map();
        assertTrue(CompactTemplateLookup.install(map));
        Map<BlockPos, Object> delegate = delegate(map);
        BlockPos key = collision(0);
        AtomicInteger callbacks = new AtomicInteger();
        List<Runnable> mutations = List.of(
                () -> delegate.put(key, "x"), () -> delegate.remove(key), () -> delegate.clear(),
                () -> delegate.putAll(Map.of()), () -> delegate.putIfAbsent(key, "x"),
                () -> delegate.remove(key, "missing"), () -> delegate.replace(key, "x"),
                () -> delegate.replace(key, "old", "new"),
                () -> delegate.replaceAll((k, v) -> { callbacks.incrementAndGet(); return v; }),
                () -> delegate.computeIfAbsent(BlockPos.ZERO, k -> { callbacks.incrementAndGet(); return "x"; }),
                () -> delegate.computeIfPresent(key, (k, v) -> { callbacks.incrementAndGet(); return v; }),
                () -> delegate.compute(key, (k, v) -> { callbacks.incrementAndGet(); return v; }),
                () -> delegate.merge(key, "x", (a, b) -> { callbacks.incrementAndGet(); return a; }),
                () -> delegate.entrySet().iterator().next().setValue("x"),
                () -> delegate.entrySet().clear(), () -> delegate.keySet().clear(), () -> delegate.values().clear(),
                () -> map.put(key, "x"), () -> map.entrySet().iterator().remove());
        for (Runnable mutation : mutations) assertThrows(UnsupportedOperationException.class, mutation::run);
        assertEquals(0, callbacks.get());
    }

    @Test void serializationOutcomeAndOrderedValuesStayIdentical() throws Exception {
        ImmutableMap<BlockPos, Object> map = map();
        Object before = serializationOutcome(map);
        Object beforeValues = serializationOutcome(map.values().asList());
        assertTrue(CompactTemplateLookup.install(map));
        assertSerializationSame(before, serializationOutcome(map));
        assertSerializationSame(beforeValues, serializationOutcome(map.values().asList()));
    }

    @Test void concurrentReadsUseCompletedIndexAndPreserveReferences() throws Exception {
        ImmutableMap<BlockPos, Object> map = map();
        Map<BlockPos, Object> oracle = delegate(map);
        assertTrue(CompactTemplateLookup.install(map));
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) jobs.add(() -> {
                for (int iteration = 0; iteration < 20; iteration++) {
                    for (BlockPos key : map.keySet()) {
                        if (map.get(new BlockPos(key.getX(), key.getY(), key.getZ())) != oracle.get(key)) return false;
                    }
                }
                return true;
            });
            for (var future : executor.invokeAll(jobs)) assertTrue(future.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static Object serializationOutcome(Object object) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(object); }
            return bytes.toByteArray();
        } catch (Exception failure) { return failure; }
    }

    private static void assertSerializationSame(Object before, Object after) {
        if (before instanceof byte[] bytes) assertArrayEquals(bytes, (byte[]) after);
        else {
            assertEquals(before.getClass(), after.getClass());
            assertEquals(((Exception) before).getMessage(), ((Exception) after).getMessage());
        }
    }

    private static final class MutableValue {
        int state;
        @Override public boolean equals(Object other) { return other instanceof MutableValue value && state == value.state; }
        @Override public int hashCode() { throw new AssertionError("installation must not hash values"); }
    }

    private static final class OddQuery {
        final BlockPos target;
        final boolean throwHash, throwEquals;
        final RuntimeException failure = new IllegalStateException("query callback");
        final List<String> callbacks = new ArrayList<>();
        OddQuery(BlockPos target, boolean throwHash, boolean throwEquals) {
            this.target = target; this.throwHash = throwHash; this.throwEquals = throwEquals;
        }
        @Override public int hashCode() {
            callbacks.add("hash");
            if (throwHash) throw failure;
            return target.hashCode();
        }
        @Override public boolean equals(Object other) {
            callbacks.add("equals:" + other);
            if (throwEquals) throw failure;
            return target.equals(other);
        }
    }
}
