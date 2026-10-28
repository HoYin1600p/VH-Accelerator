package dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals;

import static org.junit.jupiter.api.Assertions.*;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

class TrackedRecipeMapTest {
    @Test
    void nativeSingleMutatorsPreserveReturnsNullsOrderAndJournalChangedIds() {
        for (Operation operation : List.<Operation>of(
                (map, calls) -> map.put(1, "changed"),
                (map, calls) -> map.put(9, null),
                (map, calls) -> map.putIfAbsent(1, "ignored"),
                (map, calls) -> map.putIfAbsent(null, "filled"),
                (map, calls) -> map.remove(1),
                (map, calls) -> map.remove(1, "one"),
                (map, calls) -> map.remove(1, "wrong"),
                (map, calls) -> map.replace(1, "changed"),
                (map, calls) -> map.replace(1, "one", "changed"),
                (map, calls) -> map.replace(9, "absent"),
                (map, calls) -> map.computeIfAbsent(null, key -> { calls.add("absent:" + key); return "filled"; }),
                (map, calls) -> map.computeIfAbsent(1, key -> { fail("Existing value must skip callback"); return "wrong"; }),
                (map, calls) -> map.computeIfPresent(null, (key, value) -> { fail("Null value must skip callback"); return "wrong"; }),
                (map, calls) -> map.computeIfPresent(1, (key, value) -> { calls.add(value); return null; }),
                (map, calls) -> map.compute(9, (key, value) -> { calls.add("compute:" + value); return "new"; }),
                (map, calls) -> map.compute(1, (key, value) -> { calls.add(value); return null; }),
                (map, calls) -> map.merge(1, "two", (old, value) -> { calls.add(old + value); return old + value; }),
                (map, calls) -> map.merge(null, "filled", (old, value) -> { fail("Null value must skip callback"); return "wrong"; }),
                (map, calls) -> map.merge(1, null, (old, value) -> value))) {
            assertParity(operation);
        }
    }

    @Test
    void bulkMutatorsPreserveOrderNullsAndAffectedIds() {
        assertParity((map, calls) -> {
            Map<Integer, String> additions = new LinkedHashMap<>();
            additions.put(1, "changed");
            for (int key = 4; key < 80; key++) additions.put(key, "value" + key);
            map.putAll(additions);
            return null;
        });
        assertParity((map, calls) -> { map.replaceAll((key, value) -> { calls.add(key + ":" + value); return key == null ? null : value + "!"; }); return null; });
        assertParity((map, calls) -> { map.clear(); return null; });
    }

    @Test
    void partialBulkFailuresRetainNativeMutationsAndJournalThem() {
        assertParity((map, calls) -> {
            map.replaceAll((key, value) -> {
                calls.add(String.valueOf(key));
                if (calls.size() == 3) throw new IllegalStateException("third replacement");
                return value + "!";
            });
            return null;
        });
        assertParity((map, calls) -> {
            map.putAll(new AbstractMap<>() {
                @Override public Set<Entry<Integer, String>> entrySet() {
                    return new AbstractSet<>() {
                        @Override public int size() { return 2; }
                        @Override public Iterator<Entry<Integer, String>> iterator() {
                            return List.<Entry<Integer, String>>of(new SimpleImmutableEntry<>(9, "nine"),
                                    new SimpleImmutableEntry<>(10, "ten") {
                                        @Override public String getValue() { throw new IllegalArgumentException("second put"); }
                                    }).iterator();
                        }
                    };
                }
            });
            return null;
        });
    }

    @Test
    void nestedStructuralComputeAndReplaceAllPreserveCmeAndPartialEffects() {
        assertParity((map, calls) -> map.compute(1, (key, value) -> {
            calls.add(value);
            map.put(9, "nested");
            return "replacement";
        }));
        assertParity((map, calls) -> map.computeIfAbsent(9, key -> {
            calls.add(String.valueOf(key));
            map.remove(1);
            return "replacement";
        }));
        assertParity((map, calls) -> map.merge(1, "new", (old, value) -> {
            calls.add(old);
            map.put(9, "nested");
            return value;
        }));
        assertParity((map, calls) -> {
            map.replaceAll((key, value) -> {
                calls.add(String.valueOf(key));
                if (calls.size() == 1) map.put(9, "nested");
                return value + "!";
            });
            return null;
        });
    }

    @Test
    void keyValueAndEntryViewsPreserveMutationsAndNativeIteratorFailures() {
        for (Operation operation : List.<Operation>of(
                (map, calls) -> map.keySet().remove(null),
                (map, calls) -> map.keySet().removeAll(List.of(1, 2)),
                (map, calls) -> map.keySet().retainAll(List.of(1, 2)),
                (map, calls) -> map.values().remove("one"),
                (map, calls) -> map.values().removeIf(value -> value == null || value.equals("two")),
                (map, calls) -> map.values().retainAll(List.of("one", "two")),
                (map, calls) -> map.entrySet().remove(new AbstractMap.SimpleEntry<>(null, null)),
                (map, calls) -> map.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey() == 2),
                (map, calls) -> { map.keySet().clear(); return null; },
                (map, calls) -> { map.values().clear(); return null; },
                (map, calls) -> { map.entrySet().clear(); return null; },
                (map, calls) -> { var iterator = map.values().iterator(); iterator.remove(); return null; },
                (map, calls) -> { var iterator = map.keySet().iterator(); calls.add(String.valueOf(iterator.next())); iterator.remove(); iterator.remove(); return null; },
                (map, calls) -> { var iterator = map.entrySet().iterator(); iterator.next(); map.put(9, "nine"); iterator.remove(); return null; },
                (map, calls) -> { var iterator = map.values().iterator(); calls.add(iterator.next()); iterator.remove(); return null; })) {
            assertParity(operation);
        }
    }

    @Test
    void entrySetValueJournalsOverwritesButDetachedEntriesNeverReinsert() {
        assertParity((map, calls) -> map.entrySet().iterator().next().setValue("changed"));
        assertParity((map, calls) -> {
            var iterator = map.entrySet().iterator();
            var entry = iterator.next();
            iterator.remove();
            return entry.setValue("detached");
        });
    }

    @Test
    void viewForEachPreservesNativeCmeTimingAndCallbackCounts() {
        for (String view : List.of("keys", "values", "entries")) {
            assertParity((map, calls) -> {
                Collection<?> values = view(map, view);
                values.forEach(value -> {
                    calls.add(String.valueOf(value));
                    if (calls.size() == 1) map.remove(3);
                });
                return null;
            });
            assertParity((map, calls) -> {
                view(map, view).spliterator().forEachRemaining(value -> {
                    calls.add(String.valueOf(value));
                    if (calls.size() == 1) map.remove(3);
                });
                return null;
            });
        }
    }

    @Test
    void entryForEachAndSpliteratorStillJournalSetValue() {
        assertParity((map, calls) -> { map.entrySet().forEach(entry -> entry.setValue("changed")); return null; });
        assertParity((map, calls) -> { map.entrySet().spliterator().forEachRemaining(entry -> entry.setValue("changed")); return null; });
    }

    @Test
    void mapForEachAndSplitViewsPreserveNativeCallbacksAndCharacteristics() {
        assertParity((map, calls) -> {
            map.forEach((key, value) -> {
                calls.add(key + ":" + value);
                if (calls.size() == 1) map.remove(3);
            });
            return null;
        });
        for (String view : List.of("keys", "values", "entries")) {
            var nativeMap = seed();
            var tracked = new TrackedRecipeMap<Integer, String>(seed());
            var expected = view(nativeMap, view).spliterator();
            var actual = view(tracked, view).spliterator();
            assertEquals(expected.characteristics(), actual.characteristics());
            assertEquals(expected.estimateSize(), actual.estimateSize());
            var expectedSplit = expected.trySplit();
            var actualSplit = actual.trySplit();
            assertEquals(expectedSplit == null, actualSplit == null);
            if (expectedSplit != null) {
                assertEquals(expectedSplit.characteristics(), actualSplit.characteristics());
                assertEquals(expectedSplit.estimateSize(), actualSplit.estimateSize());
            }
            List<Object> expectedValues = new ArrayList<>(), actualValues = new ArrayList<>();
            if (expectedSplit != null) expectedSplit.forEachRemaining(expectedValues::add);
            if (actualSplit != null) actualSplit.forEachRemaining(actualValues::add);
            expected.forEachRemaining(expectedValues::add);
            actual.forEachRemaining(actualValues::add);
            assertEquals(expectedValues, actualValues);
        }
    }

    @Test
    void emptyViewsStillRejectNullConsumers() {
        var map = new TrackedRecipeMap<Integer, String>(new HashMap<>());
        for (String view : List.of("keys", "values", "entries")) {
            assertThrows(NullPointerException.class, () -> view(map, view).forEach(null));
            assertThrows(NullPointerException.class, () -> view(map, view).spliterator().tryAdvance(null));
            assertThrows(NullPointerException.class, () -> view(map, view).spliterator().forEachRemaining(null));
        }
    }

    @Test
    void throwingCallbackPreservesItsExceptionAndJournalsNestedPartialMutation() {
        var map = new TrackedRecipeMap<Integer, String>(seed());
        List<Integer> dirty = new ArrayList<>();
        map.observe((key, structural) -> dirty.add(key));
        RuntimeException expected = new IllegalArgumentException("callback failed");
        assertSame(expected, assertThrows(IllegalArgumentException.class, () -> map.compute(1, (key, value) -> {
            map.put(9, "partial");
            throw expected;
        })));
        assertEquals("one", map.get(1));
        assertEquals("partial", map.get(9));
        assertEquals(List.of(9), dirty);
        assertFalse(map.busy());
    }

    @Test
    void computeFamilyNotifiesBeforeAnOrderOnlyResizeWithNoChangedEntry() {
        for (String operation : List.of("computeIfAbsent", "compute", "merge")) {
            HashMap<Integer, String> raw = new HashMap<>(16);
            raw.put(0, "zero");
            raw.put(16, "sixteen");
            for (int key = 1; key <= 10; key++) raw.put(key, "value" + key);
            // HashMap compute insertion can exceed threshold without resizing until the next compute.
            raw.computeIfAbsent(11, key -> "eleven");
            var map = new TrackedRecipeMap<>(raw);
            List<Integer> before = new ArrayList<>(map.keySet());
            List<List<Integer>> notified = new ArrayList<>();
            List<Integer> dirty = new ArrayList<>();
            map.observe((key, structural) -> dirty.add(key));
            map.observeOrder(() -> notified.add(new ArrayList<>(map.keySet())));
            switch (operation) {
                case "computeIfAbsent" -> map.computeIfAbsent(0, key -> { fail("Present value skips mapping"); return "wrong"; });
                case "compute" -> map.compute(0, (key, value) -> value);
                default -> map.merge(0, "unused", (old, value) -> old);
            }
            assertEquals(List.of(before), notified, operation);
            assertNotEquals(before, new ArrayList<>(map.keySet()), "The fixture must exercise an actual resize");
            assertEquals(List.of(), dirty, operation);
            assertEquals(13, map.size());
        }
    }

    @Test
    void mutationObserversDetachWithoutInspectingRecipeValues() {
        class Recipe {
            @Override public boolean equals(Object other) { throw new AssertionError("Recipe equality must not run"); }
            @Override public int hashCode() { throw new AssertionError("Recipe hash must not run"); }
            @Override public String toString() { throw new AssertionError("Recipe getter must not run"); }
        }
        var map = new TrackedRecipeMap<Integer, Recipe>(new HashMap<>());
        List<Integer> dirty = new ArrayList<>();
        BiConsumer<Integer, Boolean> observer = (key, structural) -> dirty.add(key);
        map.observe(observer);
        assertEquals(1, map.observerCount());
        Recipe first = new Recipe();
        map.put(1, first);
        map.replace(1, new Recipe());
        map.replaceAll((key, value) -> new Recipe());
        map.entrySet().iterator().next().setValue(new Recipe());
        map.remove(1);
        assertEquals(List.of(1, 1, 1, 1, 1), dirty);
        map.unobserve(observer);
        assertEquals(0, map.observerCount());
        map.put(2, first);
        assertEquals(5, dirty.size());
    }

    @Test
    void ownerCallbacksAreBusyAndForeignWritesDisableEligibilityWithoutJournaling() throws Exception {
        var map = new TrackedRecipeMap<Integer, String>(seed());
        AtomicInteger events = new AtomicInteger();
        map.observe((key, structural) -> events.incrementAndGet());
        map.compute(1, (key, value) -> { assertTrue(map.busy()); return value; });
        assertFalse(map.busy());
        assertTrue(map.eligible());
        Thread foreign = new Thread(() -> map.put(9, "foreign"));
        foreign.start();
        foreign.join(5_000);
        assertFalse(foreign.isAlive());
        assertFalse(map.eligible());
        assertEquals(0, events.get());
        assertEquals("foreign", map.get(9));
    }

    private static Collection<?> view(Map<Integer, String> map, String view) {
        return switch (view) { case "keys" -> map.keySet(); case "values" -> map.values(); default -> map.entrySet(); };
    }

    private static HashMap<Integer, String> seed() {
        HashMap<Integer, String> result = new HashMap<>();
        result.put(null, null);
        result.put(1, "one");
        result.put(2, "two");
        result.put(3, "three");
        return result;
    }

    @FunctionalInterface private interface Operation {
        Object apply(Map<Integer, String> map, List<String> calls);
    }

    private record Result(Object value, Class<?> failure, String message, List<String> calls) { }

    private static Result execute(Operation operation, Map<Integer, String> map) {
        List<String> calls = new ArrayList<>();
        try { return new Result(operation.apply(map, calls), null, null, calls); }
        catch (RuntimeException failure) { return new Result(null, failure.getClass(), failure.getMessage(), calls); }
    }

    private static void assertParity(Operation operation) {
        HashMap<Integer, String> nativeMap = seed();
        var tracked = new TrackedRecipeMap<Integer, String>(seed());
        Map<Integer, String> before = new HashMap<>(tracked);
        List<Integer> dirty = new ArrayList<>();
        tracked.observe((key, structural) -> dirty.add(key));
        Result expected = execute(operation, nativeMap);
        Result actual = execute(operation, tracked);
        assertEquals(expected, actual);
        assertEquals(nativeMap, tracked);
        assertEquals(snapshot(nativeMap), snapshot(tracked), "Native encounter order must remain unchanged");
        Set<Integer> all = new java.util.HashSet<>(before.keySet());
        all.addAll(tracked.keySet());
        for (Integer key : all) {
            if (before.containsKey(key) != tracked.containsKey(key) || before.get(key) != tracked.get(key)) {
                assertTrue(dirty.contains(key), "Missing dirty ID: " + key);
            }
        }
    }

    private static List<Map.Entry<Integer, String>> snapshot(Map<Integer, String> map) {
        return map.entrySet().stream().map(AbstractMap.SimpleImmutableEntry::new)
                .map(entry -> (Map.Entry<Integer, String>) entry).toList();
    }
}
