package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class LazyStateModelCacheTest {
    /** States resolve to "baked:<state>#"; locations are "<state>#". */
    private static LazyStateModelCache<String, String, String> cache(
            List<String> states,
            Function<String, String> resolver
    ) {
        return new LazyStateModelCache<>(
                states, state -> state + "#", resolver, location -> true);
    }

    private static LazyStateModelCache<String, String, String> cache(
            String... states
    ) {
        return cache(Arrays.asList(states), location -> "baked:" + location);
    }

    @Test
    void firstReadResolvesAndLaterReadsUseTheCachedModel() {
        AtomicInteger calls = new AtomicInteger();
        var cache = cache(List.of("state"), location -> {
            calls.incrementAndGet();
            return "baked:" + location;
        });

        assertEquals(1, cache.size());
        assertTrue(cache.containsKey("state"));
        assertTrue(cache.keySet().contains("state"));
        assertTrue(cache.isPending("state"));
        assertEquals(0, calls.get());
        assertEquals("baked:state#", cache.get("state"));
        assertEquals("baked:state#", cache.get("state"));
        assertEquals(1, calls.get());
        assertFalse(cache.isPending("state"));
        assertEquals(0, cache.pendingCount());
    }

    @Test
    void resolveAtOnlyReplacesAPendingSlot() {
        var cache = cache("a", "b");
        int a = cache.keyAt(0).equals("a") ? 0 : 1;
        assertTrue(cache.resolveAt(a, "eager"));
        assertFalse(cache.resolveAt(a, "second"), "construction never overwrites");
        assertEquals("eager", cache.get("a"));
        cache.remove("b");
        int b = 1 - a;
        assertFalse(cache.resolveAt(b, "late"), "a removed state stays removed");
        assertFalse(cache.containsKey("b"));
    }

    @Test
    void nullAndDuplicateConstructionKeysAreIgnored() {
        var cache = cache("a", null, "a", "b");
        assertEquals(2, cache.size());
        assertEquals(2, cache.fixedSize());
        assertEquals(Set.of("a", "b"), new HashSet<>(cache.keySet()));
        assertNull(cache.get(null));
        assertFalse(cache.containsKey(null));
        assertNull(cache.remove(null));
        assertThrows(NullPointerException.class, () -> cache.put(null, "x"));
    }

    @Test
    void nullValuesArePresent() {
        var cache = cache("a");
        assertNull(cache.put("a", null), "a pending previous value never bakes");
        assertTrue(cache.containsKey("a"));
        assertNull(cache.get("a"));
        assertFalse(cache.isPending("a"));
        assertEquals(1, cache.size());
        cache.put("extra", null);
        assertTrue(cache.containsKey("extra"));
        assertEquals(2, cache.size());
        assertFalse(cache.entrySet().contains(Map.entry("a", "missing")));
    }

    @Test
    void unknownStatesUseOrdinaryMapSemantics() {
        AtomicInteger calls = new AtomicInteger();
        var cache = cache(List.of("known"), location -> {
            calls.incrementAndGet();
            return "baked:" + location;
        });
        assertNull(cache.get("unknown"));
        assertFalse(cache.containsKey("unknown"));
        assertNull(cache.put("unknown", "direct"));
        assertEquals("direct", cache.get("unknown"));
        assertEquals(2, cache.size());
        assertEquals("direct", cache.put("unknown", "newer"));
        assertEquals("newer", cache.remove("unknown"));
        assertFalse(cache.containsKey("unknown"));
        assertEquals(1, cache.size());
        assertEquals(0, calls.get(), "unknown states never resolve");
    }

    @Test
    void removeAndPutKeepSizeAndKeysConsistent() {
        var cache = cache("a", "b", "c");
        assertEquals("baked:a#", cache.get("a"));
        assertEquals("baked:a#", cache.remove("a"));
        assertNull(cache.remove("a"));
        assertNull(cache.get("a"), "a removed state never resolves again");
        assertNull(cache.remove("b"), "removing a pending state never bakes");
        assertEquals(1, cache.size());
        assertEquals(Set.of("c"), new HashSet<>(cache.keySet()));
        assertNull(cache.put("a", "back"));
        assertEquals(2, cache.size());
        assertEquals("back", cache.get("a"));
        assertTrue(cache.keySet().remove("c"));
        assertFalse(cache.keySet().remove("c"));
        assertEquals(Set.of("a"), new HashSet<>(cache.keySet()));
        cache.put("extra", "x");
        cache.clear();
        assertTrue(cache.isEmpty());
        assertFalse(cache.keySet().iterator().hasNext());
        assertNull(cache.get("b"));
    }

    @Test
    void iterationNeverBakesAndSupportsRemoval() {
        AtomicInteger calls = new AtomicInteger();
        var cache = cache(List.of("a", "b", "c"), location -> {
            calls.incrementAndGet();
            return "baked:" + location;
        });
        cache.put("extra", "x");
        List<String> keys = new ArrayList<>(cache.keySet());
        assertEquals(List.of("a", "b", "c", "extra"), keys, "construction order, then others");
        for (Iterator<String> it = cache.keySet().iterator(); it.hasNext();) {
            String key = it.next();
            if (key.equals("b") || key.equals("extra")) {
                it.remove();
            }
        }
        assertEquals(0, calls.get(), "key iteration and removal never bake");
        assertEquals(Set.of("a", "c"), new HashSet<>(cache.keySet()));
        Iterator<String> empty = cache.keySet().iterator();
        assertThrows(IllegalStateException.class, empty::remove);

        List<Map.Entry<String, String>> entries = new ArrayList<>(cache.entrySet());
        assertEquals(0, calls.get(), "collecting entries never bakes");
        assertEquals("baked:a#", entries.get(0).getValue());
        assertEquals(1, calls.get(), "an entry bakes only its own state");
        assertTrue(cache.entrySet().contains(Map.entry("c", "baked:c#")));
        assertTrue(cache.entrySet().remove(Map.entry("c", "baked:c#")));
        assertFalse(cache.containsKey("c"));
        assertEquals(Map.of("a", "baked:a#"), Map.copyOf(cache));
    }

    @Test
    void entrySetValueWritesAndFailsAfterRemoval() {
        var cache = cache("a");
        cache.put("extra", "x");
        Map.Entry<String, String> a = null;
        Map.Entry<String, String> extra = null;
        for (Map.Entry<String, String> entry : cache.entrySet()) {
            if (entry.getKey().equals("a")) {
                a = entry;
            } else {
                extra = entry;
            }
        }
        assertNull(a.setValue("set"), "replacing a pending value never bakes");
        assertEquals("set", cache.get("a"));
        assertEquals("set", a.setValue("again"));
        assertEquals("x", extra.setValue("y"));
        assertEquals("y", cache.get("extra"));
        cache.remove("a");
        cache.remove("extra");
        Map.Entry<String, String> removed = a;
        Map.Entry<String, String> removedExtra = extra;
        assertThrows(IllegalStateException.class, () -> removed.setValue("z"));
        assertThrows(IllegalStateException.class, () -> removedExtra.setValue("z"));
        assertTrue(cache.isEmpty());
    }

    @Test
    void directWriteWinsOverAConcurrentResolution() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var cache = cache(List.of("state"), location -> {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("resolver did not resume");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            return "stale";
        });
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            var read = worker.submit(() -> cache.get("state"));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            cache.put("state", "newer");
            release.countDown();
            assertEquals("newer", read.get(5, TimeUnit.SECONDS));
            assertEquals("newer", cache.get("state"));
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void removalWinsOverAConcurrentResolution() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var cache = cache(List.of("state"), location -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return "stale";
        });
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            var read = worker.submit(() -> cache.get("state"));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertNull(cache.remove("state"));
            release.countDown();
            assertNull(read.get(5, TimeUnit.SECONDS));
            assertFalse(cache.containsKey("state"));
            assertEquals(0, cache.size());
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void uncacheableResultsAreReturnedButNeverCached() {
        AtomicBoolean cacheable = new AtomicBoolean(false);
        AtomicInteger calls = new AtomicInteger();
        var cache = new LazyStateModelCache<String, String, String>(
                List.of("state", "other"),
                state -> state + "#",
                location -> {
                    calls.incrementAndGet();
                    return "missing";
                },
                location -> cacheable.get());

        assertEquals("missing", cache.get("state"));
        assertEquals("missing", cache.get("state"));
        assertTrue(cache.isPending("state"), "a retired or unpublished read is never kept");
        assertEquals(2, calls.get());
        cacheable.set(true);
        assertEquals("missing", cache.get("state"));
        assertFalse(cache.isPending("state"));
        assertEquals(3, calls.get());

        var nullResolver = new LazyStateModelCache<String, String, String>(
                List.of("state"), state -> state, location -> null, location -> true);
        assertNull(nullResolver.get("state"));
        assertTrue(nullResolver.isPending("state"), "a missing model is never cached");
    }

    @Test
    void concurrentReadsWritesAndRemovalsStayConsistent() throws Exception {
        List<Integer> states = new ArrayList<>();
        for (int i = 0; i < 4096; i++) {
            states.add(i);
        }
        var cache = new LazyStateModelCache<Integer, Integer, String>(
                states, state -> state, location -> "model" + location, location -> true);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                tasks.add(pool.submit(() -> {
                    for (int i = 0; i < 4096; i++) {
                        String model = cache.get(i);
                        if (i % 3 == 0) {
                            // Possibly overwritten or removed by the writer.
                            assertTrue(model == null || model.equals("model" + i)
                                    || model.equals("direct" + i), model);
                        } else {
                            assertEquals("model" + i, model);
                        }
                    }
                }));
            }
            tasks.add(pool.submit(() -> {
                for (int i = 0; i < 4096; i += 3) {
                    if (i % 2 == 0) {
                        cache.put(i, "direct" + i);
                    } else {
                        cache.remove(i);
                    }
                }
            }));
            tasks.add(pool.submit(() -> {
                for (int round = 0; round < 8; round++) {
                    int count = 0;
                    for (Integer ignored : cache.keySet()) {
                        count++;
                    }
                    assertTrue(count <= 4096);
                }
            }));
            for (Future<?> task : tasks) {
                task.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        int removed = 0;
        for (int i = 0; i < 4096; i += 3) {
            if (i % 2 == 0) {
                assertEquals("direct" + i, cache.get(i), "direct writes win");
            } else {
                assertFalse(cache.containsKey(i));
                removed++;
            }
        }
        assertEquals(4096 - removed, cache.size());
        assertEquals(4096 - removed, new HashSet<>(cache.keySet()).size());
        assertEquals(0, cache.pendingCount());
    }

    @Test
    void estimateGrowsWithStateCount() {
        List<Integer> states = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            states.add(i);
        }
        var small = new LazyStateModelCache<Integer, Integer, String>(
                List.of(1), state -> state, location -> "m", location -> true);
        var large = new LazyStateModelCache<Integer, Integer, String>(
                states, state -> state, location -> "m", location -> true);
        assertTrue(large.estimatedBytes() > small.estimatedBytes());
        // Keys, slots and an index at most 3/4 full: well under 32 B per state.
        assertTrue(large.estimatedBytes() / 1000 < 32, "compact per-state footprint");
    }
}
