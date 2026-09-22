package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        assertEquals(Set.of("block"), cache.keySet(), "pending items are not iterated");
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
