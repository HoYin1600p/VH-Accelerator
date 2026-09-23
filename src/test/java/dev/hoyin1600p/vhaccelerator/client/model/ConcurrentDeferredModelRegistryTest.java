package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ConcurrentDeferredModelRegistryTest {
    private final List<String> baked = Collections.synchronizedList(new ArrayList<>());
    private final List<String> failed = Collections.synchronizedList(new ArrayList<>());

    private ConcurrentDeferredModelRegistry<String, String> registry(Function<String, String> baker) {
        Map<String, String> eager = MutationTrackingMap.ownFreshMap(new LinkedHashMap<>());
        eager.put("a:block#inventory", "item");
        eager.put("minecraft:missing", "missing");
        return new ConcurrentDeferredModelRegistry<>(eager, List.of("a:stone#", "b:log#axis=y"), key -> {
            baked.add(key);
            return baker.apply(key);
        }, "missing", (key, nanos, didFail, failure) -> {
            if (didFail) {
                failed.add(key);
            }
        });
    }

    private ConcurrentDeferredModelRegistry<String, String> registry() {
        return registry(key -> "baked(" + key + ")");
    }

    @Test void keyViewsAgreeWithoutBaking() {
        var map = registry();
        assertEquals(4, map.size());
        assertTrue(map.containsKey("a:stone#"));
        assertFalse(map.containsKey("c:dirt#"));
        assertEquals(Set.of("a:block#inventory", "minecraft:missing", "a:stone#", "b:log#axis=y"),
                new HashSet<>(map.keySet()));
        int entries = 0;
        for (var entry : map.entrySet()) {
            assertNotNull(entry.getKey());
            entries++;
        }
        assertEquals(4, entries);
        assertTrue(baked.isEmpty(), "key iteration must not bake");
        assertEquals(2, map.unresolvedDeferred());
    }

    @Test void eagerKeysAreNeverDeferred() {
        Map<String, String> eager = new HashMap<>(Map.of("a:stone#", "eager"));
        var map = new ConcurrentDeferredModelRegistry<String, String>(eager, List.of("a:stone#"),
                key -> "baked", "missing", (k, n, f, e) -> { });
        assertEquals(0, map.initialDeferred());
        assertEquals("eager", map.get("a:stone#"));
    }

    @Test void valuesBakeOnceOnFirstAccess() {
        var map = registry();
        assertTrue(map.isUnresolvedDeferred("a:stone#"));
        assertEquals("baked(a:stone#)", map.get("a:stone#"));
        assertEquals("baked(a:stone#)", map.getOrDefault("a:stone#", "x"));
        assertEquals(List.of("a:stone#"), baked);
        assertFalse(map.isUnresolvedDeferred("a:stone#"));
        assertEquals(1, map.bakedOnDemand());
        assertEquals("x", map.getOrDefault("c:dirt#", "x"));
    }

    @Test void entryValueBakesOnlyThatKey() {
        var map = registry();
        for (var entry : map.entrySet()) {
            if (entry.getKey().equals("b:log#axis=y")) {
                assertEquals("baked(b:log#axis=y)", entry.getValue());
            }
        }
        assertEquals(List.of("b:log#axis=y"), baked);
    }

    @Test void putReturnsRealPreviousValueAndOverrides() {
        var map = registry();
        assertEquals("baked(a:stone#)", map.put("a:stone#", "wrapped"));
        assertEquals("wrapped", map.get("a:stone#"));
        assertTrue(map.isDeferred("a:stone#"));
        assertEquals(4, map.size());
        // A second override replaces without another bake.
        assertEquals("wrapped", map.put("a:stone#", "wrapped2"));
        assertEquals(List.of("a:stone#"), baked);
        assertNull(map.put("c:new#", "added"));
        assertEquals(5, map.size());
    }

    @Test void setValueOverridesLikeForgeCallbacks() {
        var map = registry();
        for (var entry : map.entrySet()) {
            if (entry.getKey().equals("a:stone#")) {
                entry.setValue("wrap(" + entry.getValue() + ")");
            }
        }
        assertEquals("wrap(baked(a:stone#))", map.get("a:stone#"));
    }

    @Test void removeAndKeyRemovalKeepViewsConsistent() {
        var map = registry();
        assertEquals("baked(a:stone#)", map.remove("a:stone#"));
        assertFalse(map.containsKey("a:stone#"));
        assertNull(map.get("a:stone#"));
        assertTrue(map.keySet().remove("b:log#axis=y"));
        assertEquals(List.of("a:stone#"), baked, "key-set removal must not bake");
        assertEquals(2, map.size());
        assertEquals(2, map.removedDeferred());
        var keys = map.keySet().iterator();
        while (keys.hasNext()) {
            if (keys.next().equals("minecraft:missing")) {
                keys.remove();
            }
        }
        assertEquals(Set.of("a:block#inventory"), new HashSet<>(map.keySet()));
    }

    @Test void structuralVersionTracksDeferredRemovals() {
        var map = registry();
        long before = map.structuralVersion();
        assertTrue(before >= 0);
        map.get("a:stone#");
        assertEquals(before, map.structuralVersion(), "a bake is not structural");
        map.keySet().remove("a:stone#");
        assertNotEquals(before, map.structuralVersion());
    }

    @Test void failedBakeFallsBackToMissingOnce() {
        var map = registry(key -> {
            throw new IllegalStateException("boom");
        });
        assertEquals("missing", map.get("a:stone#"));
        assertEquals("missing", map.get("a:stone#"));
        assertEquals(List.of("a:stone#"), baked);
        assertEquals(List.of("a:stone#"), failed);
        var nullMap = registry(key -> null);
        assertEquals("missing", nullMap.get("b:log#axis=y"));
        assertEquals(1, nullMap.failedBakes());
    }

    @Test void fatalBakeErrorReachesEveryWaitingReader() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AssertionError fatal = new AssertionError("fatal bake");
        var map = new ConcurrentDeferredModelRegistry<String, String>(
                new HashMap<>(), List.of("x"), key -> {
                    if (calls.incrementAndGet() > 1) {
                        return "recovered";
                    }
                    started.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    throw fatal;
                }, "missing", (key, nanos, failed, failure) -> { }
        );
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> owner = pool.submit(() -> map.get("x"));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<String> waiter = pool.submit(() -> map.get("x"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (map.sharedWaits() < 1) {
                assertTrue(System.nanoTime() < deadline, "waiter did not join the bake");
                Thread.sleep(1);
            }
            release.countDown();
            ExecutionException ownerFailure = assertThrows(
                    ExecutionException.class, () -> owner.get(10, TimeUnit.SECONDS));
            ExecutionException waiterFailure = assertThrows(
                    ExecutionException.class, () -> waiter.get(10, TimeUnit.SECONDS));
            assertSame(fatal, ownerFailure.getCause());
            assertSame(fatal, waiterFailure.getCause());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertEquals(1, calls.get());
        assertTrue(map.isUnresolvedDeferred("x"));
        assertEquals("recovered", map.get("x"));
    }

    @Test void retiredRegistryNeverBakesOrCaches() {
        var map = registry();
        map.get("a:stone#");
        map.retire();
        assertTrue(map.isRetired());
        assertEquals("baked(a:stone#)", map.get("a:stone#"), "resolved values survive retirement");
        assertEquals("missing", map.get("b:log#axis=y"));
        assertEquals(List.of("a:stone#"), baked);
        assertEquals(1, map.retiredLookups());
        assertTrue(map.containsKey("b:log#axis=y"), "keys stay present after retirement");
        assertFalse(map.isUnresolvedDeferred("b:log#axis=y"));
    }

    @Test void reentrantLookupReadsAbsent() {
        AtomicReference<ConcurrentDeferredModelRegistry<String, String>> self = new AtomicReference<>();
        AtomicReference<String> inner = new AtomicReference<>("unset");
        var map = registry(key -> {
            inner.set(self.get().getOrDefault(key, "absent"));
            return "baked";
        });
        self.set(map);
        assertEquals("baked", map.get("a:stone#"));
        assertEquals("absent", inner.get());
    }

    @Test void concurrentFirstUseBakesEachKeyOnce() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        Map<String, String> eager = new HashMap<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            keys.add("m:block" + i + "#");
        }
        var map = new ConcurrentDeferredModelRegistry<String, String>(eager, keys, key -> {
            calls.incrementAndGet();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return "baked(" + key + ")";
        }, "missing", (k, n, f, e) -> { });
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                results.add(pool.submit(() -> {
                    for (String key : keys) {
                        if (!("baked(" + key + ")").equals(map.get(key))) {
                            return false;
                        }
                    }
                    return true;
                }));
            }
            Thread.sleep(50);
            release.countDown();
            for (Future<Boolean> result : results) {
                assertTrue(result.get(10, TimeUnit.SECONDS), "every reader sees the real model");
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(64, calls.get(), "each key bakes exactly once");
        assertEquals(0, map.unresolvedDeferred());
    }

    @Test void differentKeysBakeInParallel() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        var map = new ConcurrentDeferredModelRegistry<String, String>(new HashMap<>(), List.of("x", "y"), key -> {
            bothStarted.countDown();
            try {
                return bothStarted.await(5, TimeUnit.SECONDS) ? "ok" : "serialized";
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return null;
            }
        }, "missing", (k, n, f, e) -> { });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> x = pool.submit(() -> map.get("x"));
            Future<String> y = pool.submit(() -> map.get("y"));
            assertEquals("ok", x.get(10, TimeUnit.SECONDS));
            assertEquals("ok", y.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void retireWaitsForBakeInProgress() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicBoolean bakeDone = new AtomicBoolean();
        var map = new ConcurrentDeferredModelRegistry<String, String>(new HashMap<>(), List.of("x"), key -> {
            started.countDown();
            try {
                finish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            bakeDone.set(true);
            return "ok";
        }, "missing", (k, n, f, e) -> { });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> worker = pool.submit(() -> map.get("x"));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<?> retire = pool.submit(map::retire);
            Thread.sleep(50);
            assertFalse(retire.isDone(), "retire must wait for the running bake");
            finish.countDown();
            retire.get(10, TimeUnit.SECONDS);
            assertTrue(bakeDone.get());
            assertEquals("ok", worker.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void lazyStateCacheResolvesOnFirstReadAndCachesWhileLive() {
        AtomicInteger resolves = new AtomicInteger();
        AtomicBoolean live = new AtomicBoolean(true);
        Map<String, String> locations = Map.of(
                "stone", "stone#", "log", "log#axis=y", "dirt", "dirt#");
        LazyStateModelCache<String, String, String> cache = new LazyStateModelCache<>(
                List.of("stone", "log", "dirt"),
                locations::get,
                location -> {
                    resolves.incrementAndGet();
                    return "model(" + location + ")";
                }, location -> live.get());
        assertTrue(cache.resolveAt(0, "eager"));
        assertEquals(3, cache.size());
        assertTrue(cache.containsKey("log"));
        assertEquals(Set.of("stone", "log", "dirt"), new HashSet<>(cache.keySet()));
        assertEquals(0, resolves.get(), "key views never resolve");
        assertEquals("model(log#axis=y)", cache.get("log"));
        assertEquals("model(log#axis=y)", cache.get("log"));
        assertEquals(1, resolves.get());
        assertFalse(cache.isPending("log"));
        live.set(false);
        assertEquals("model(dirt#)", cache.get("dirt"));
        assertTrue(cache.isPending("dirt"), "a retired generation is never cached");
        assertNull(cache.put("dirt", "override"), "replacing a pending state never resolves");
        assertEquals("override", cache.get("dirt"));
        assertEquals("eager", cache.get("stone"));
        assertNull(cache.get("unknown"));
    }

    @Test void lazyStateCacheConcurrentReadersAlwaysSeeRealModels() throws Exception {
        List<Integer> states = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            states.add(i);
        }
        LazyStateModelCache<Integer, Integer, String> cache = new LazyStateModelCache<>(
                states, state -> state, location -> "model" + location, location -> true);
        Set<String> seen = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                tasks.add(pool.submit(() -> {
                    for (int i = 0; i < 256; i++) {
                        String model = cache.get(i);
                        assertEquals("model" + i, model);
                        seen.add(model);
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(256, seen.size());
        assertEquals(0, cache.pendingCount());
    }

    @Test void itemLayerNeverWaitsForAWorkerBlockBake() throws Exception {
        CountDownLatch blockStarted = new CountDownLatch(1);
        CountDownLatch releaseBlock = new CountDownLatch(1);
        Map<String, String> base = new HashMap<>(Map.of("minecraft:missing", "missing"));
        var blocks = new ConcurrentDeferredModelRegistry<String, String>(base, List.of("a:stone#"), key -> {
            blockStarted.countDown();
            try {
                releaseBlock.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return "block";
        }, "missing", (k, n, f, e) -> { });
        var items = new DeferredModelRegistry<String, String>(blocks, List.of("a:stone#inventory"),
                key -> "item", "missing", (key, failure) -> { });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> worker = pool.submit(() -> items.get("a:stone#"));
            assertTrue(blockStarted.await(5, TimeUnit.SECONDS));
            // The item bake needs the item write lock; the block bake must not hold it.
            Future<String> render = pool.submit(() -> items.get("a:stone#inventory"));
            assertEquals("item", render.get(2, TimeUnit.SECONDS));
            assertEquals("missing", items.getOrDefault("c:absent#", "missing"));
            assertTrue(items.containsKey("a:stone#"));
            releaseBlock.countDown();
            assertEquals("block", worker.get(10, TimeUnit.SECONDS));
            assertEquals("block", items.getOrDefault("a:stone#", "x"));
            assertEquals(3, items.size(), "missing + block key + item key");
        } finally {
            releaseBlock.countDown();
            pool.shutdownNow();
        }
    }

    @Test void onlyUnprotectedBlockStateKeysAreEligible() {
        assertTrue(DeferredBlockStateBaking.eligibleKey(mrl("minecraft", "stone", "")));
        assertTrue(DeferredBlockStateBaking.eligibleKey(mrl("create", "shaft", "axis=y")));
        assertFalse(DeferredBlockStateBaking.eligibleKey(mrl("minecraft", "stone", "inventory")));
        assertFalse(DeferredBlockStateBaking.eligibleKey(
                new net.minecraft.resources.ResourceLocation("minecraft", "block/stone")));
        for (String namespace : List.of("the_vault", "everycomp", "buildscape", "ctm",
                "sophisticatedbackpacks", "sophisticatedstorage")) {
            assertFalse(DeferredBlockStateBaking.eligibleKey(mrl(namespace, "thing", "")), namespace);
        }
    }

    private static net.minecraft.client.resources.model.ModelResourceLocation mrl(
            String namespace, String path, String variant) {
        return new net.minecraft.client.resources.model.ModelResourceLocation(
                new net.minecraft.resources.ResourceLocation(namespace, path), variant);
    }

    @Test void nullTolerantConcurrentCopyAcceptsVanillaProbes() {
        Map<String, String> source = new HashMap<>();
        source.put("a", "1");
        source.put("b", null);
        Map<String, String> copy = DeferredBlockStateBaking.concurrentCopy(source);
        assertNull(copy.get(null));
        assertFalse(copy.containsKey(null));
        assertEquals("1", copy.get("a"));
        assertFalse(copy.containsKey("b"));
        assertNull(copy.put("c", null));
        assertSame(copy, DeferredBlockStateBaking.concurrentCopy(copy));
    }
}
