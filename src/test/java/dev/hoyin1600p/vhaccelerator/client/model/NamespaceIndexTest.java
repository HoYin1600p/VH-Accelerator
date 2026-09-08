package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NamespaceIndexTest {
    @Test void preSizingPreservesOwnedIdentityAndExistingViews() {
        var map = MutationTrackingMap.<String, String>ownFreshMap(new HashMap<>());
        var index = new NamespaceIndex<>(map, NamespaceIndexTest::namespace);
        var entries = map.entrySet();
        assertTrue(index.keys("a").isEmpty());
        assertTrue(map.reserveIfEmpty(128));
        assertEquals(0, map.structuralVersion());
        map.put("a:model", "model");
        assertEquals(Set.of("a:model"), index.keys("a"));
        assertEquals(Set.of(Map.entry("a:model", "model")), entries);
        assertFalse(map.reserveIfEmpty(256));
        assertEquals("model", map.get("a:model"));
        var foreign = MutationTrackingMap.<String, String>ownFreshMap(new LinkedHashMap<>());
        assertFalse(foreign.reserveIfEmpty(128));
    }
    private static String namespace(String key) { return key.split(":")[0]; }
    private static MutationTrackingMap<String, String> registry() {
        var map = MutationTrackingMap.<String, String>ownFreshMap(new LinkedHashMap<>());
        map.put("a:old", "old");
        map.put("b:other", "other");
        return map;
    }

    @Test void equalSizeKeySwapInvalidatesButValueReplacementDoesNot() {
        var map = registry();
        var index = new NamespaceIndex<>(map, NamespaceIndexTest::namespace);
        Set<String> view = index.keys("a");
        assertEquals(Set.of("a:old"), view);
        int builds = index.builds();
        map.put("a:old", "replacement");
        assertEquals(Set.of("a:old"), view);
        assertEquals(builds, index.builds());
        map.remove("a:old");
        map.put("a:new", "new");
        assertEquals(Set.of("a:new"), view);
        assertEquals(builds + 1, index.builds());
    }

    @Test void foreignMapNeverReusesASizeOnlySnapshot() {
        var map = new LinkedHashMap<>(Map.of("a:old", "old", "b:other", "other"));
        var index = new NamespaceIndex<>(map, NamespaceIndexTest::namespace);
        assertEquals(Set.of("a:old"), index.keys("a"));
        map.remove("a:old");
        map.put("a:new", "new");
        assertEquals(Set.of("a:new"), index.keys("a"));
    }

    @Test void filteredViewsAreBackedAndEntryValuesStayLive() {
        var map = registry();
        var index = new NamespaceIndex<>(map, NamespaceIndexTest::namespace);
        var entries = index.entries("a");
        var entry = entries.iterator().next();
        assertEquals("old", entry.setValue("wrapped"));
        assertEquals("wrapped", map.get("a:old"));
        map.put("a:old", "newer");
        assertEquals("newer", entry.getValue());
        assertEquals(Map.entry("a:old", "newer"), entry);
        assertEquals(Map.entry("a:old", "newer").hashCode(), entry.hashCode());
        map.put("a:second", "second");
        assertEquals(2, entries.size());
        assertTrue(entries.remove(Map.entry("a:old", "newer")));
        assertFalse(map.containsKey("a:old"));
        index.keys("a").clear();
        assertEquals(Map.of("b:other", "other"), map);
    }

    @Test void allMutationRoutesInvalidateTheIndex() {
        var map = registry();
        var index = new NamespaceIndex<>(map, NamespaceIndexTest::namespace);
        index.keys("a").size();
        map.compute("a:old", (key, value) -> null);
        map.computeIfAbsent("a:computed", key -> "computed");
        assertEquals(Set.of("a:computed"), index.keys("a"));
        map.merge("a:merged", "merged", (a, b) -> a + b);
        map.values().remove("computed");
        assertEquals(Set.of("a:merged"), index.keys("a"));
        map.entrySet().removeIf(entry -> entry.getKey().equals("a:merged"));
        map.putAll(Map.of("a:put", "put", "a:null", "null"));
        map.replace("a:null", null);
        assertTrue(index.keys("a").remove("a:null"));
        assertEquals(Set.of("a:put"), index.keys("a"));
        var iterator = map.keySet().iterator();
        while (iterator.hasNext()) { iterator.next(); iterator.remove(); }
        assertTrue(index.keys("a").isEmpty());
    }

    @Test void repeatedNamespaceDoesNotWrapValuesTwice() {
        var map = registry();
        var index = new NamespaceIndex<>(map, NamespaceIndexTest::namespace);
        index.replaceAll(List.of("a", "a"), (key, value) -> "wrap(" + value + ")");
        assertEquals("wrap(old)", map.get("a:old"));
        assertEquals("other", map.get("b:other"));
    }
}
