package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
        assertEquals(1, map.unresolvedDeferred(), "retirement bakes nothing");
    }

    @Test void onlyActualLookupsResolveKeys() {
        var map = registry();
        assertEquals(2, map.initialDeferred());
        assertEquals(2, map.unresolvedDeferred());
        // Registry-wide key consumers, as at menu or level join, bake nothing.
        map.size();
        map.containsKey("b:item");
        new HashSet<>(map.keySet());
        map.entrySet().forEach(Map.Entry::getKey);
        assertTrue(baked.isEmpty());
        assertEquals(2, map.unresolvedDeferred());
        assertTrue(map.isUnresolvedDeferred("b:item"));
        assertFalse(map.isUnresolvedDeferred("a:block"));
        assertTrue(map.isDeferred("b:item"));
        assertFalse(map.isDeferred("a:block"));
        map.getOrDefault("a:item", "missing");
        assertEquals(List.of("a:item"), baked);
        assertEquals(1, map.unresolvedDeferred());
        assertFalse(map.isUnresolvedDeferred("a:item"));
        assertTrue(map.isDeferred("a:item"), "baked keys stay deferred keys");
    }

    @Test void unresolvedCountTracksRemovalAndReplacement() {
        var map = registry();
        map.keySet().remove("b:item");
        assertEquals(1, map.unresolvedDeferred());
        assertEquals(1, map.removedDeferred());
        map.put("a:item", "wrapped");
        assertEquals(0, map.unresolvedDeferred());
        assertEquals(List.of("a:item"), baked, "put reports the baked previous value");
    }

    @Test void nullAndUnknownKeysNeverBake() {
        Map<String, String> eager = new HashMap<>(Map.of("a:block", "block"));
        var map = new DeferredModelRegistry<>(eager, java.util.Arrays.asList("a:item", null),
                key -> { baked.add(key); return "lazy"; }, "missing", (k, f) -> failed.add(k));
        assertEquals(1, map.initialDeferred(), "null keys are never deferred");
        assertNull(map.get(null));
        assertEquals("fallback", map.getOrDefault(null, "fallback"));
        assertFalse(map.containsKey(null));
        assertNull(map.get("unknown:item"));
        assertNull(map.get(42));
        assertFalse(map.isDeferred(null));
        assertTrue(baked.isEmpty());
        assertTrue(failed.isEmpty());
    }

    @Test void failedBakeIsCachedAndCountedOnce() {
        var map = registry(key -> { throw new LinkageError("broken"); });
        assertEquals("missing", map.getOrDefault("a:item", "default"));
        assertEquals("missing", map.getOrDefault("a:item", "default"));
        assertEquals(1, map.failedBakes());
        assertEquals(0, map.bakedOnDemand());
        assertEquals(1, map.unresolvedDeferred());
        assertEquals(List.of("a:item"), baked);
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

    /** Runs every task at once and rethrows the first failure. */
    private static void runTogether(List<Callable<?>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Eager map that records any write overlapping a read or another write. */
    private static final class GuardedMap extends HashMap<String, String> {
        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicInteger writes = new AtomicInteger();
        private final AtomicBoolean overlap = new AtomicBoolean();

        private void read() {
            reads.incrementAndGet();
            if (writes.get() != 0) {
                overlap.set(true);
            }
        }

        private void write() {
            if (writes.incrementAndGet() != 1 || reads.get() != 0) {
                overlap.set(true);
            }
        }

        @Override public String get(Object key) {
            read();
            try {
                return super.get(key);
            } finally {
                reads.decrementAndGet();
            }
        }

        @Override public boolean containsKey(Object key) {
            read();
            try {
                return super.containsKey(key);
            } finally {
                reads.decrementAndGet();
            }
        }

        @Override public String put(String key, String value) {
            write();
            try {
                return super.put(key, value);
            } finally {
                writes.decrementAndGet();
            }
        }

        @Override public String remove(Object key) {
            write();
            try {
                return super.remove(key);
            } finally {
                writes.decrementAndGet();
            }
        }
    }

    @Test void eagerReadsProceedConcurrently() throws Exception {
        int readers = 4;
        CyclicBarrier together = new CyclicBarrier(readers);
        Map<String, String> eager = new HashMap<>() {
            @Override public String get(Object key) {
                try {
                    // Completes only if every reader is inside the map at once.
                    together.await(10, TimeUnit.SECONDS);
                } catch (Exception serialized) {
                    throw new IllegalStateException("eager reads were serialized", serialized);
                }
                return super.get(key);
            }
        };
        eager.put("a:block", "block");
        var map = new DeferredModelRegistry<>(eager, List.of("a:item"),
                key -> "lazy", "missing", (k, f) -> { });
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < readers; i++) {
            boolean withDefault = i % 2 == 0;
            tasks.add(() -> {
                String value = withDefault
                        ? map.getOrDefault("a:block", "default")
                        : map.get("a:block");
                assertEquals("block", value);
                return null;
            });
        }
        runTogether(tasks);
    }

    @Test void contendedDeferredKeyBakesOnce() throws Exception {
        Map<String, AtomicInteger> bakes = new ConcurrentHashMap<>();
        CountDownLatch bakeStarted = new CountDownLatch(1);
        CountDownLatch finishBake = new CountDownLatch(1);
        var map = new DeferredModelRegistry<>(new HashMap<>(Map.of("a:block", "block")),
                List.of("a:item", "b:item"), key -> {
                    bakes.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
                    if (key.equals("a:item")) {
                        bakeStarted.countDown();
                        try {
                            finishBake.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                    return "baked(" + key + ")";
                }, "missing", (k, f) -> { });
        ExecutorService first = Executors.newSingleThreadExecutor();
        try {
            Future<String> firstBake = first.submit(() -> map.get("a:item"));
            assertTrue(bakeStarted.await(10, TimeUnit.SECONDS));
            List<Callable<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String key = i % 2 == 0 ? "a:item" : "b:item";
                tasks.add(() -> {
                    assertEquals("baked(" + key + ")", map.getOrDefault(key, "default"));
                    return null;
                });
            }
            tasks.add(() -> {
                finishBake.countDown();
                return null;
            });
            runTogether(tasks);
            assertEquals("baked(a:item)", firstBake.get(10, TimeUnit.SECONDS));
        } finally {
            first.shutdownNow();
        }
        assertEquals(1, bakes.get("a:item").get());
        assertEquals(1, bakes.get("b:item").get());
        assertEquals(2, map.bakedOnDemand());
        assertEquals(0, map.unresolvedDeferred());
    }

    @Test void putWaitingOnAnotherThreadsBakeReplacesIt() throws Exception {
        CountDownLatch bakeStarted = new CountDownLatch(1);
        CountDownLatch finishBake = new CountDownLatch(1);
        AtomicInteger bakes = new AtomicInteger();
        var map = new DeferredModelRegistry<>(new HashMap<String, String>(),
                List.of("a:item"), key -> {
                    bakes.incrementAndGet();
                    bakeStarted.countDown();
                    try {
                        finishBake.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                    return "baked";
                }, "missing", (k, f) -> { });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> bake = pool.submit(() -> map.get("a:item"));
            assertTrue(bakeStarted.await(10, TimeUnit.SECONDS));
            Future<String> put = pool.submit(() -> map.put("a:item", "override"));
            finishBake.countDown();
            assertEquals("baked", bake.get(10, TimeUnit.SECONDS));
            assertEquals("baked", put.get(10, TimeUnit.SECONDS), "put saw the finished bake");
        } finally {
            pool.shutdownNow();
        }
        assertEquals("override", map.get("a:item"));
        assertEquals(1, bakes.get());
    }

    @Test void writesNeverOverlapConcurrentReads() throws Exception {
        GuardedMap eager = new GuardedMap();
        eager.put("a:block", "block");
        Set<String> bakedKeys = ConcurrentHashMap.newKeySet();
        AtomicInteger bakes = new AtomicInteger();
        List<String> deferredKeys = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            deferredKeys.add("d:" + i);
        }
        var map = new DeferredModelRegistry<>(eager, deferredKeys, key -> {
            assertTrue(bakedKeys.add(key), "baked twice: " + key);
            bakes.incrementAndGet();
            return "baked(" + key + ")";
        }, "missing", (k, f) -> { });
        List<Callable<?>> tasks = new ArrayList<>();
        for (int reader = 0; reader < 4; reader++) {
            tasks.add(() -> {
                for (int i = 0; i < 2_000; i++) {
                    assertEquals("block", map.get("a:block"));
                    map.containsKey("c:" + (i % 8));
                    map.getOrDefault("c:" + (i % 8), "default");
                    String deferred = map.get("d:" + (i % 64));
                    assertTrue(deferred == null || deferred.startsWith("baked(")
                            || deferred.equals("missing") || deferred.startsWith("put"),
                            String.valueOf(deferred));
                }
                return null;
            });
        }
        tasks.add(() -> {
            for (int i = 0; i < 2_000; i++) {
                map.put("c:" + (i % 8), "c");
                map.remove("c:" + ((i + 4) % 8));
                if (i % 50 == 0) {
                    map.remove("d:" + (i / 50));
                }
                if (i % 70 == 0) {
                    map.put("d:" + (32 + i / 70), "put" + i);
                }
            }
            map.retire();
            return null;
        });
        runTogether(tasks);
        assertFalse(eager.overlap.get(), "a write overlapped a read of the eager map");
        assertTrue(map.isRetired());
        int bakedBefore = bakes.get();
        for (String key : deferredKeys) {
            String value = map.get(key);
            if (map.containsKey(key)) {
                assertNotNull(value, key);
            }
        }
        assertEquals(bakedBefore, bakes.get(), "nothing bakes after retirement");
        assertEquals(eager.size() + map.initialDeferred() - map.removedDeferred(), map.size());
        assertEquals(map.size(), new HashSet<>(map.keySet()).size());
    }

    @Test void offThreadLookupsNeverBakeAndRetirementWins() throws Exception {
        Thread owner = Thread.currentThread();
        List<String> bakedKeys = java.util.Collections.synchronizedList(new ArrayList<>());
        var map = new DeferredModelRegistry<>(new HashMap<>(Map.of("a:block", "block")),
                List.of("a:item", "b:item"), key -> {
                    bakedKeys.add(key);
                    return "baked(" + key + ")";
                }, "missing", (k, f) -> { }, () -> Thread.currentThread() == owner);
        int threads = 4;
        int lookups = 500;
        List<Callable<?>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            tasks.add(() -> {
                for (int i = 0; i < lookups; i++) {
                    assertEquals("missing", map.get("a:item"));
                    assertEquals("missing", map.getOrDefault("b:item", "default"));
                }
                return null;
            });
        }
        runTogether(tasks);
        assertTrue(bakedKeys.isEmpty(), "off-thread lookups must not bake");
        assertEquals(threads * lookups * 2, map.offThreadLookups());
        assertEquals(2, map.unresolvedDeferred(), "fallbacks are not cached");

        assertEquals("baked(a:item)", map.get("a:item"));
        List<Callable<?>> racing = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            racing.add(() -> {
                for (int i = 0; i < lookups; i++) {
                    assertEquals("baked(a:item)", map.get("a:item"));
                    assertEquals("missing", map.get("b:item"));
                }
                return null;
            });
        }
        racing.add(() -> {
            map.retire();
            return null;
        });
        runTogether(racing);
        assertTrue(map.isRetired());
        assertEquals("missing", map.get("b:item"));
        assertEquals(List.of("a:item"), bakedKeys);
        assertTrue(map.retiredLookups() >= 1);
    }
}
