package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class DeferredModelRegistryTest {
    private final List<String> baked = new ArrayList<>();
    private final List<String> failed = new ArrayList<>();

    private DeferredModelRegistry<String, String> registry(Function<String, String> baker) {
        Map<String, String> eager = MutationTrackingMap.ownFreshMap(new LinkedHashMap<>());
        eager.put("a:block", "block");
        eager.put("minecraft:missing", "missing");
        return new DeferredModelRegistry<>(eager, List.of("a:item", "b:item"), key -> {
            baked.add(key);
            return baker.apply(key);
        }, "missing", (key, failure) -> failed.add(key));
    }

    private DeferredModelRegistry<String, String> registry() {
        return registry(key -> "baked(" + key + ")");
    }

    @Test void keyViewsAgreeWithoutBaking() {
        var map = registry();
        assertEquals(4, map.size());
        assertTrue(map.containsKey("a:item"));
        assertFalse(map.containsKey("c:item"));
        assertEquals(Set.of("a:block", "minecraft:missing", "a:item", "b:item"), new HashSet<>(map.keySet()));
        assertTrue(map.keySet().contains("b:item"));
        int entries = 0;
        for (var entry : map.entrySet()) {
            assertNotNull(entry.getKey());
            entries++;
        }
        assertEquals(4, entries);
        assertTrue(baked.isEmpty(), "key iteration must not bake");
    }

    @Test void valuesBakeOnceOnFirstAccess() {
        var map = registry();
        assertEquals("baked(a:item)", map.get("a:item"));
        assertEquals("baked(a:item)", map.get("a:item"));
        assertEquals("block", map.get("a:block"));
        assertNull(map.get("c:item"));
        assertEquals("fallback", map.getOrDefault("c:item", "fallback"));
        assertEquals(List.of("a:item"), baked);
        assertEquals(1, map.bakedOnDemand());
        assertEquals(Map.of("a:block", "block", "minecraft:missing", "missing",
                "a:item", "baked(a:item)", "b:item", "baked(b:item)"), new HashMap<>(map));
    }

    @Test void entryValuesAreLazyAndWritable() {
        var map = registry();
        for (var entry : map.entrySet()) {
            if (entry.getKey().startsWith("b:")) {
                assertEquals("baked(b:item)", entry.setValue("wrapped"));
            }
        }
        assertEquals(List.of("b:item"), baked);
        assertEquals("wrapped", map.get("b:item"));
        assertTrue(map.entrySet().contains(Map.entry("b:item", "wrapped")));
    }

    @Test void getDuringIterationIsNotStructural() {
        var map = registry();
        for (String key : map.keySet()) {
            map.put(key, "wrap(" + map.get(key) + ")");
        }
        assertEquals("wrap(baked(a:item))", map.get("a:item"));
        assertEquals("wrap(block)", map.get("a:block"));
        assertEquals(4, map.size());
    }

    @Test void putAndRemoveReturnPreviousLogicalValue() {
        var map = registry();
        assertEquals("baked(a:item)", map.put("a:item", "override"));
        assertEquals("override", map.get("a:item"));
        assertEquals("baked(b:item)", map.remove("b:item"));
        assertFalse(map.containsKey("b:item"));
        assertNull(map.get("b:item"));
        assertEquals(3, map.size());
        assertNull(map.put("c:new", "new"));
        assertTrue(map.containsKey("c:new"));
    }

    @Test void explicitNullValuesFollowHashMapSemantics() {
        var map = registry();
        map.put("a:item", null);
        assertTrue(map.containsKey("a:item"));
        assertNull(map.get("a:item"));
        assertNull(map.getOrDefault("a:item", "default"));
    }

    @Test void iteratorRemovalCoversBothKeySources() {
        var map = registry();
        var iterator = map.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().endsWith(":item")) {
                iterator.remove();
            }
        }
        assertEquals(Set.of("a:block", "minecraft:missing"), map.keySet());
        assertTrue(baked.isEmpty());
        map.clear();
        assertTrue(map.isEmpty());
    }

    @Test void failedOrNullBakesUseFallbackConsistently() {
        var map = registry(key -> {
            if (key.startsWith("a:")) {
                throw new IllegalStateException("broken");
            }
            return null;
        });
        assertEquals("missing", map.get("a:item"));
        assertEquals("missing", map.get("b:item"));
        assertTrue(map.containsKey("a:item"));
        assertEquals(List.of("a:item", "b:item"), failed);
        assertEquals(2, map.failedBakes());
        map.get("a:item");
        assertEquals(2, baked.size(), "failures are cached, not retried");
    }

    @Test void reentrantLookupIsAbsentWhileBaking() {
        AtomicReference<DeferredModelRegistry<String, String>> self = new AtomicReference<>();
        List<Object> observed = new ArrayList<>();
        var map = registry(key -> {
            observed.add(self.get().get(key));
            observed.add(self.get().getOrDefault(key, "default"));
            return "baked";
        });
        self.set(map);
        assertEquals("baked", map.get("a:item"));
        assertEquals(java.util.Arrays.asList(null, "default"), observed);
        assertEquals("baked", map.get("a:item"));
    }

    @Test void reentrantPutDuringBakeWins() {
        AtomicReference<DeferredModelRegistry<String, String>> self = new AtomicReference<>();
        var map = registry(key -> {
            self.get().put(key, "override");
            return "baked";
        });
        self.set(map);
        assertEquals("override", map.get("a:item"));
        assertEquals("override", map.get("a:item"));
    }

    @Test void retirementNeverBakesAgainstStaleState() {
        var map = registry();
        map.get("a:item");
        map.retire();
        assertTrue(map.isRetired());
        assertFalse(map.isUnresolvedDeferred("b:item"));
        assertEquals("baked(a:item)", map.get("a:item"));
        assertEquals("missing", map.get("b:item"));
        assertTrue(map.containsKey("b:item"));
        assertEquals(List.of("a:item"), baked);
        assertEquals(1, map.retiredLookups());
        assertEquals(0, map.warm(Long.MAX_VALUE, System::nanoTime));
    }

    @Test void warmupRespectsBudgetAndSkipsResolvedKeys() {
        var map = registry();
        long[] now = {0};
        map.get("a:item");
        assertTrue(map.isUnresolvedDeferred("b:item"));
        assertFalse(map.isUnresolvedDeferred("a:item"));
        assertFalse(map.isUnresolvedDeferred("a:block"));
        int remaining = map.warm(0, () -> now[0]);
        assertEquals(1, remaining, "at least one key per step");
        assertEquals(List.of("a:item"), baked, "already-resolved key is skipped");
        assertEquals(0, map.warm(0, () -> now[0]));
        assertEquals(List.of("a:item", "b:item"), baked);
        assertFalse(map.isUnresolvedDeferred("b:item"));
    }

    @Test void eagerKeysWinOverDuplicateDeferredKeys() {
        Map<String, String> eager = new HashMap<>(Map.of("a:item", "eager"));
        var map = new DeferredModelRegistry<>(eager, List.of("a:item"), key -> "lazy", "missing", (k, f) -> { });
        assertEquals(1, map.size());
        assertEquals("eager", map.get("a:item"));
        assertEquals(0, map.initialDeferred());
    }

    @Test void structuralVersionTracksKeySetOnly() {
        var map = registry();
        long start = map.structuralVersion();
        assertTrue(start >= 0);
        map.get("a:item");
        map.put("a:item", "replacement");
        map.put("a:block", "replacement");
        assertEquals(start, map.structuralVersion());
        map.remove("a:item");
        assertTrue(map.structuralVersion() > start);
        long afterRemove = map.structuralVersion();
        map.put("c:new", "new");
        assertTrue(map.structuralVersion() > afterRemove);
        var unversioned = new DeferredModelRegistry<>(new HashMap<String, String>(), List.of("x"), k -> "v", "m", (k, f) -> { });
        assertEquals(-1, unversioned.structuralVersion());
    }

    @Test void namespaceIndexServesDeferredKeysWithoutBaking() {
        var map = registry();
        var index = new NamespaceIndex<>(map, key -> key.split(":")[0]);
        assertEquals(Set.of("b:item"), index.keys("b"));
        assertTrue(baked.isEmpty());
        index.replaceAll(List.of("b"), (key, value) -> "wrap(" + value + ")");
        assertEquals(List.of("b:item"), baked);
        assertEquals("wrap(baked(b:item))", map.get("b:item"));
        int builds = index.builds();
        index.keys("b").size();
        assertEquals(builds, index.builds(), "value replacement keeps the index");
    }
}
