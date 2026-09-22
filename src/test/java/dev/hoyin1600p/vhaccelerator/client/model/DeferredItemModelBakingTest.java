package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Forge item-cache semantics used by {@link DeferredItemModelBaking}: the
 * cache replaces Forge's {@code models} map, which both
 * {@code getItemModel(ItemStack)} and {@code getItemModel(Item)} read.
 */
class DeferredItemModelBakingTest {
    private final List<String> baked = new ArrayList<>();
    private final Thread owner = Thread.currentThread();

    private DeferredModelRegistry<String, String> registry() {
        Map<String, String> eager = new HashMap<>(Map.of("block#inventory", "eager", "missing", "missing"));
        return new DeferredModelRegistry<>(eager, List.of("a#inventory", "b#inventory"), key -> {
            baked.add(key);
            return "baked(" + key + ")";
        }, "missing", (k, f) -> { }, () -> Thread.currentThread() == owner);
    }

    /** Mirrors rebuildItemCache over Forge's location map. */
    private DeferredModelRegistry.ItemCache<String, String, String> rebuild(
            DeferredModelRegistry<String, String> registry,
            Map<String, String> forgeModels
    ) {
        var cache = new DeferredModelRegistry.ItemCache<String, String, String>(
                forgeModels, () -> "manager-missing", () -> Thread.currentThread() == owner);
        cache.bind(registry);
        Map<String, String> locations = Map.of("itemA", "a#inventory", "itemB", "b#inventory", "block", "block#inventory");
        locations.forEach((item, location) -> {
            if (registry.isUnresolvedDeferred(location)) {
                cache.defer(item, location);
            } else {
                cache.put(item, registry.getOrDefault(location, "missing"));
            }
        });
        return cache;
    }

    @Test void rebuildBakesNothingAndDropsStaleEntries() {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>(Map.of("itemA", "stale", "block", "stale")));
        assertTrue(baked.isEmpty(), "rebuild must not bake deferred items");
        assertEquals(2, cache.pendingCount());
        assertEquals("eager", cache.get("block"));
        assertTrue(cache.containsKey("itemA"));
        assertEquals(Set.of("itemA", "itemB", "block"), cache.keySet(), "pending items are logically present");
        assertEquals(3, cache.size());
        assertTrue(baked.isEmpty());
    }

    @Test void mapViewsDescribeOneKeyUnionWithoutBaking() {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        Set<String> expected = Set.of("itemA", "itemB", "block");
        assertEquals(3, cache.size());
        assertFalse(cache.isEmpty());
        assertEquals(expected, new HashSet<>(cache.keySet()));
        assertEquals(3, cache.keySet().size());
        assertEquals(3, cache.entrySet().size());
        Set<String> entryKeys = new HashSet<>();
        for (var entry : cache.entrySet()) {
            entryKeys.add(entry.getKey());
        }
        assertEquals(expected, entryKeys);
        for (String key : expected) {
            assertTrue(cache.containsKey(key));
            assertTrue(cache.keySet().contains(key));
        }
        assertTrue(baked.isEmpty(), "size, keys and entry iteration never bake");
        assertEquals(2, cache.pendingCount());
    }

    @Test void readingOneEntryValueBakesOnlyThatItem() {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        for (var entry : cache.entrySet()) {
            if (entry.getKey().equals("itemB")) {
                assertEquals("baked(b#inventory)", entry.getValue());
            }
        }
        assertEquals(List.of("b#inventory"), baked);
        assertFalse(cache.isPending("itemB"));
        assertTrue(cache.isPending("itemA"));
        assertEquals(3, cache.size(), "resolving never changes the key set");
        assertTrue(cache.entrySet().contains(Map.entry("itemB", "baked(b#inventory)")));
        assertEquals(List.of("b#inventory"), baked);
    }

    @Test void valuesAndCopiesReadEveryValueOnTheOwnerThread() {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        assertEquals(3, cache.values().size());
        assertTrue(baked.isEmpty(), "values().size() never bakes");
        assertEquals(Map.of("itemA", "baked(a#inventory)", "itemB", "baked(b#inventory)", "block", "eager"),
                new HashMap<>(cache));
        assertEquals(0, cache.pendingCount());
    }

    @Test void keyRemovalsDropPendingItemsWithoutBaking() {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        assertTrue(cache.keySet().remove("itemA"));
        assertFalse(cache.keySet().remove("itemA"));
        assertFalse(cache.containsKey("itemA"));
        var iterator = cache.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().equals("itemB")) {
                iterator.remove();
            }
        }
        assertEquals(Set.of("block"), cache.keySet());
        assertEquals(1, cache.size());
        assertNull(cache.get("itemB"));
        assertEquals(0, cache.pendingCount());
        assertTrue(baked.isEmpty());
    }

    @Test void entryIteratorRemovalAndSetValueNeverBakePendingItems() {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().equals("itemA")) {
                assertNull(entry.setValue("wrapped"), "a pending previous value is never baked");
            } else if (entry.getKey().equals("itemB")) {
                iterator.remove();
            }
        }
        assertTrue(baked.isEmpty());
        assertEquals("wrapped", cache.get("itemA"));
        assertFalse(cache.isPending("itemA"));
        assertFalse(cache.containsKey("itemB"));
        assertEquals(Set.of("itemA", "block"), cache.keySet());
        assertEquals(2, cache.entrySet().size());
    }

    @Test void removedEntryRejectsSetValue() {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        Map.Entry<String, String> itemA = null;
        for (var entry : cache.entrySet()) {
            if (entry.getKey().equals("itemA")) {
                itemA = entry;
            }
        }
        assertNotNull(itemA);
        cache.remove("itemA");
        Map.Entry<String, String> removed = itemA;
        assertThrows(IllegalStateException.class, () -> removed.setValue("late"));
        assertFalse(cache.containsKey("itemA"));
        assertTrue(baked.isEmpty());
    }

    @Test void entrySetContainsAndRemoveTouchOnlyTheirItem() {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        assertFalse(cache.entrySet().contains(Map.entry("itemA", "other")));
        assertEquals(List.of("a#inventory"), baked);
        assertFalse(cache.entrySet().remove(Map.entry("itemA", "other")));
        assertTrue(cache.containsKey("itemA"));
        assertTrue(cache.entrySet().remove(Map.entry("itemA", "baked(a#inventory)")));
        assertFalse(cache.containsKey("itemA"));
        assertFalse(cache.entrySet().contains(Map.entry("unknown", "x")));
        assertEquals(List.of("a#inventory"), baked, "itemB was never read");
        assertTrue(cache.isPending("itemB"));
    }

    @Test void putRemoveAndClearKeepViewsConsistent() {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        assertNull(cache.remove("itemA"), "removing a pending item never bakes");
        assertEquals(2, cache.size());
        assertNull(cache.put("itemC", "new"));
        assertEquals(Set.of("itemB", "block", "itemC"), cache.keySet());
        assertEquals("new", cache.put("itemC", "newer"));
        assertEquals(3, cache.size());
        cache.clear();
        assertTrue(cache.isEmpty());
        assertEquals(0, cache.size());
        assertTrue(cache.keySet().isEmpty());
        assertTrue(cache.entrySet().isEmpty());
        assertNull(cache.get("itemB"));
        assertTrue(baked.isEmpty());
    }

    @Test void concurrentReadersSeeStableKeysWhileOwnerResolves() throws Exception {
        List<String> locations = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            locations.add("m" + i + "#inventory");
        }
        var registry = new DeferredModelRegistry<String, String>(new HashMap<>(), locations,
                key -> "baked(" + key + ")", "missing", (k, f) -> { },
                () -> Thread.currentThread() == owner);
        var cache = new DeferredModelRegistry.ItemCache<String, String, String>(
                Map.of("block", "eager"), () -> "manager-missing", () -> Thread.currentThread() == owner);
        cache.bind(registry);
        for (int i = 0; i < locations.size(); i++) {
            cache.defer("item" + i, locations.get(i));
        }
        int total = locations.size() + 1;
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<String> problem = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            while (!done.get() && problem.get() == null) {
                if (cache.size() != total) {
                    problem.set("size " + cache.size());
                }
                int keys = 0;
                for (String key : cache.keySet()) {
                    keys++;
                }
                if (keys != total) {
                    problem.set("keys " + keys);
                }
                int entries = 0;
                for (var entry : cache.entrySet()) {
                    entries++;
                    if (entry.getValue() == null) {
                        problem.set("null value for " + entry.getKey());
                    }
                }
                if (entries != total) {
                    problem.set("entries " + entries);
                }
                for (int i = 0; i < locations.size(); i++) {
                    String item = "item" + i;
                    if (!cache.containsKey(item) || cache.get(item) == null) {
                        problem.set("missing " + item);
                    }
                }
            }
        });
        reader.start();
        for (int i = 0; i < locations.size(); i++) {
            assertEquals("baked(" + locations.get(i) + ")", cache.get("item" + i));
            Thread.yield();
        }
        done.set(true);
        reader.join();
        assertNull(problem.get());
        assertEquals(0, cache.pendingCount());
        assertEquals(total, cache.size());
        assertEquals(locations.size(), registry.bakedOnDemand(), "readers never bake");
    }

    @Test void offThreadEntryValuesReturnFallbackUncached() throws Exception {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        List<Object> seen = new ArrayList<>();
        Thread worker = new Thread(() -> {
            for (var entry : cache.entrySet()) {
                if (entry.getKey().startsWith("item")) {
                    seen.add(entry.getValue());
                }
            }
        });
        worker.start();
        worker.join();
        assertEquals(List.of("missing", "missing"), seen);
        assertTrue(baked.isEmpty());
        assertEquals(2, cache.pendingCount());
    }

    @Test void releasedCacheKeepsPendingKeysVisibleWithMissingValues() {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        registry.retire();
        cache.release();
        assertEquals(3, cache.size());
        assertEquals(Set.of("itemA", "itemB", "block"), cache.keySet());
        for (var entry : cache.entrySet()) {
            assertNotNull(entry.getValue());
        }
        assertEquals(2, cache.pendingCount());
        assertTrue(baked.isEmpty());
    }

    @Test void directLookupResolvesOnceAndCaches() {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        // Forge's getItemModel(Item) is models.get(item.delegate).
        assertEquals("baked(a#inventory)", cache.get("itemA"));
        assertEquals("baked(a#inventory)", cache.get("itemA"));
        assertEquals("baked(a#inventory)", cache.getOrDefault("itemA", "x"));
        assertEquals(List.of("a#inventory"), baked);
        assertFalse(cache.isPending("itemA"));
        assertTrue(cache.isPending("itemB"));
        assertEquals(1, registry.bakedOnDemand());
        assertNull(cache.get("unknown"), "unregistered items stay absent");
        assertNull(cache.get(null));
    }

    @Test void itemsSharingALocationBakeOnce() {
        var registry = registry();
        var cache = new DeferredModelRegistry.ItemCache<String, String, String>(
                Map.of(), () -> "manager-missing", () -> true);
        cache.bind(registry);
        cache.defer("item1", "a#inventory");
        cache.defer("item2", "a#inventory");
        assertEquals(cache.get("item1"), cache.get("item2"));
        assertEquals(List.of("a#inventory"), baked);
    }

    @Test void offThreadLookupNeverBakesOrCaches() throws Exception {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        AtomicReference<Object> seen = new AtomicReference<>();
        Thread worker = new Thread(() -> seen.set(cache.get("itemA")));
        worker.start();
        worker.join();
        assertEquals("missing", seen.get(), "off-thread reads the fallback");
        assertTrue(baked.isEmpty(), "no bake off the render thread");
        assertTrue(cache.isPending("itemA"), "the fallback is not cached");
        assertEquals(1, registry.offThreadLookups());
        assertEquals("baked(a#inventory)", cache.get("itemA"));
        assertEquals(List.of("a#inventory"), baked);
        Thread reader = new Thread(() -> seen.set(cache.get("itemA")));
        reader.start();
        reader.join();
        assertEquals("baked(a#inventory)", seen.get(), "baked models are visible off-thread");
    }

    @Test void registryRefusesOffThreadBakesThroughEveryAccessor() throws Exception {
        var registry = registry();
        List<Object> seen = new ArrayList<>();
        Thread worker = new Thread(() -> {
            seen.add(registry.get("a#inventory"));
            seen.add(registry.getOrDefault("b#inventory", "default"));
            registry.entrySet().forEach(Map.Entry::getValue);
        });
        worker.start();
        worker.join();
        assertEquals(List.of("missing", "missing"), seen);
        assertTrue(baked.isEmpty());
        assertEquals(2, registry.unresolvedDeferred());
        assertTrue(registry.isUnresolvedDeferred("a#inventory"));
    }

    @Test void forgePutAndRegisterReplacePendingItems() {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        assertNull(cache.put("itemA", "registered"), "put never bakes a previous value");
        assertEquals("registered", cache.get("itemA"));
        assertFalse(cache.isPending("itemA"));
        assertTrue(baked.isEmpty());
        cache.remove("itemB");
        assertFalse(cache.containsKey("itemB"));
        assertNull(cache.get("itemB"));
        assertTrue(baked.isEmpty());
    }

    @Test void reentrantLookupDuringBakeIsMissingAndUncached() {
        AtomicReference<DeferredModelRegistry.ItemCache<String, String, String>> self = new AtomicReference<>();
        List<Object> nested = new ArrayList<>();
        var registry = new DeferredModelRegistry<String, String>(new HashMap<>(), List.of("a#inventory"), key -> {
            nested.add(self.get().get("itemA"));
            return "baked";
        }, "missing", (k, f) -> { });
        var cache = new DeferredModelRegistry.ItemCache<String, String, String>(
                Map.of(), () -> "manager-missing", () -> true);
        self.set(cache);
        cache.bind(registry);
        cache.defer("itemA", "a#inventory");
        assertEquals("baked", cache.get("itemA"));
        assertEquals(List.of("manager-missing"), nested);
        assertEquals("baked", cache.get("itemA"));
    }

    @Test void retirementAndReleaseStopBakingUntilRebuild() {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        cache.get("itemA");
        registry.retire();
        cache.release();
        assertFalse(cache.isBound());
        assertEquals("baked(a#inventory)", cache.get("itemA"), "cached items survive until rebuilt");
        assertEquals("manager-missing", cache.get("itemB"), "pending items never bake after retirement");
        assertTrue(cache.isPending("itemB"), "the missing model is not cached");
        assertEquals(List.of("a#inventory"), baked);
        // Forge's original rebuild for an eager reload overwrites every item.
        cache.put("itemA", "new-a");
        cache.put("itemB", "new-b");
        assertEquals(0, cache.pendingCount());
        assertEquals("new-b", cache.get("itemB"));
    }

    @Test void retiredRegistryStillBoundNeverCachesFallback() {
        var registry = registry();
        var cache = rebuild(registry, new HashMap<>());
        registry.retire();
        assertEquals("missing", cache.get("itemA"));
        assertTrue(cache.isPending("itemA"));
        assertTrue(baked.isEmpty());
    }

    @Test void nextDeferredReloadRebindsTheSameCache() {
        var first = registry();
        var cache = rebuild(first, new HashMap<>());
        cache.get("itemA");
        first.retire();
        cache.release();
        baked.clear();
        var second = registry();
        cache.bind(second);
        cache.defer("itemA", "a#inventory");
        assertTrue(cache.isPending("itemA"), "the old bake does not survive the reload");
        assertEquals("baked(a#inventory)", cache.get("itemA"));
        assertEquals(List.of("a#inventory"), baked);
        assertEquals(1, second.bakedOnDemand());
    }

    @Test void rebindKeepsItemsVisibleUntilRedeferredOrReplaced() {
        var first = registry();
        var cache = rebuild(first, new HashMap<>());
        first.retire();
        cache.release();
        var second = registry();
        cache.bind(second);
        assertTrue(cache.isPending("itemB"), "binding alone never drops an item");
        assertEquals("baked(b#inventory)", cache.get("itemB"));
        assertEquals(1, second.bakedOnDemand());
        cache.put("itemA", "eager-now");
        assertFalse(cache.isPending("itemA"));
        assertEquals("eager-now", cache.get("itemA"));
    }

    @Test void nullValuesAndEntryWritesFollowHashMapSemantics() {
        var cache = new DeferredModelRegistry.ItemCache<String, String, String>(
                new HashMap<>(Map.of("x", "model")), () -> "manager-missing", () -> true);
        cache.put("y", null);
        assertTrue(cache.containsKey("y"));
        assertNull(cache.get("y"));
        for (var entry : cache.entrySet()) {
            entry.setValue("wrapped(" + entry.getValue() + ")");
        }
        assertEquals("wrapped(model)", cache.get("x"));
        assertEquals("wrapped(null)", cache.get("y"));
        assertEquals(2, cache.size());
    }
}
