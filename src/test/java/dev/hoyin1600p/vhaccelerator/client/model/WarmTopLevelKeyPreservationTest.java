package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Registry behaviour the warm top-level stage relies on: keys whose graph was
 * never loaded are deferred registry keys like any other, load only on their
 * first value read, and never vanish from the key views.
 */
class WarmTopLevelKeyPreservationTest {
    private final List<String> loaded = new ArrayList<>();
    private final List<String> baked = new ArrayList<>();
    private final Set<String> skipped = Set.of("mod:skipped_a#inventory", "mod:skipped_b#inventory");

    private DeferredModelRegistry<String, String> registry(Set<String> unloadable) {
        Map<String, String> eager = new HashMap<>(Map.of(
                "mod:block#facing=north", "eager-block", "missing", "missing"));
        Set<String> deferred = new LinkedHashSet<>(List.of("mod:selected#inventory"));
        deferred.addAll(skipped);
        return new DeferredModelRegistry<>(eager, deferred, key -> {
            if (skipped.contains(key)) {
                loaded.add(key); // getModel + getMaterials on first use
                if (unloadable.contains(key)) {
                    return null;
                }
            }
            baked.add(key);
            return "baked(" + key + ")";
        }, "missing", (key, failure) -> { });
    }

    @Test void skippedKeysArePresentWithoutLoading() {
        var registry = registry(Set.of());
        assertEquals(5, registry.size());
        for (String key : skipped) {
            assertTrue(registry.containsKey(key));
            assertTrue(registry.keySet().contains(key));
            assertTrue(registry.isUnresolvedDeferred(key));
        }
        Set<String> iterated = new HashSet<>();
        for (Map.Entry<String, String> entry : registry.entrySet()) {
            iterated.add(entry.getKey());
        }
        assertTrue(iterated.containsAll(skipped));
        assertTrue(loaded.isEmpty(), "key views never load a graph");
        assertTrue(baked.isEmpty());
    }

    @Test void firstLookupLoadsAndBakesOnlyThatKeyOnce() {
        var registry = registry(Set.of());
        assertEquals("baked(mod:skipped_a#inventory)", registry.get("mod:skipped_a#inventory"));
        assertEquals("baked(mod:skipped_a#inventory)",
                registry.getOrDefault("mod:skipped_a#inventory", "missing"));
        assertEquals(List.of("mod:skipped_a#inventory"), loaded);
        assertEquals(List.of("mod:skipped_a#inventory"), baked);
        assertTrue(registry.isUnresolvedDeferred("mod:skipped_b#inventory"));
    }

    @Test void bakeEventReplacementWinsAndKeepsTheKey() {
        var registry = registry(Set.of());
        registry.put("mod:skipped_b#inventory", "wrapped");
        assertEquals("wrapped", registry.get("mod:skipped_b#inventory"));
        assertTrue(registry.containsKey("mod:skipped_b#inventory"));
        assertEquals(5, registry.size());
    }

    @Test void failedLoadMatchesEagerMissingModelAndKeepsTheKey() {
        var registry = registry(Set.of("mod:skipped_a#inventory"));
        assertEquals("missing", registry.get("mod:skipped_a#inventory"));
        assertTrue(registry.containsKey("mod:skipped_a#inventory"));
        assertEquals(1, registry.failedBakes());
    }

    @Test void retirementNeverLoadsAGraph() {
        var registry = registry(Set.of());
        registry.retire();
        assertEquals("missing", registry.get("mod:skipped_a#inventory"));
        assertTrue(registry.containsKey("mod:skipped_a#inventory"));
        assertTrue(loaded.isEmpty());
    }

    @Test void offThreadLookupNeverLoadsAGraph() throws Exception {
        Thread owner = Thread.currentThread();
        Map<String, String> eager = new HashMap<>();
        var registry = new DeferredModelRegistry<String, String>(eager, skipped, key -> {
            loaded.add(key);
            return "baked";
        }, "missing", (key, failure) -> { }, () -> Thread.currentThread() == owner);
        List<String> seen = new ArrayList<>();
        Thread worker = new Thread(() -> seen.add(registry.get("mod:skipped_a#inventory")));
        worker.start();
        worker.join();
        assertEquals(List.of("missing"), seen);
        assertTrue(loaded.isEmpty());
        assertTrue(registry.isUnresolvedDeferred("mod:skipped_a#inventory"));
    }
}
