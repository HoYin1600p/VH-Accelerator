package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LazyStateModelCacheTest {
    @Test
    void firstReadResolvesAndLaterReadsUseTheCachedModel() {
        AtomicInteger calls = new AtomicInteger();
        var cache = new LazyStateModelCache<String, String, String>(
                1, location -> {
                    calls.incrementAndGet();
                    return "baked:" + location;
                }, () -> true);
        cache.defer("state", "model");

        assertEquals(1, cache.size());
        assertTrue(cache.containsKey("state"));
        assertTrue(cache.keySet().contains("state"));
        assertEquals(0, calls.get());
        assertEquals("baked:model", cache.get("state"));
        assertEquals("baked:model", cache.get("state"));
        assertEquals(1, calls.get());
        assertFalse(cache.isPending("state"));
    }

    @Test
    void directWriteWinsOverAConcurrentResolution() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var cache = new LazyStateModelCache<String, String, String>(
                1, location -> {
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
                }, () -> true);
        cache.defer("state", "model");
        var worker = Executors.newSingleThreadExecutor();
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
    void retiredLookupNeverCachesItsFallback() {
        var cache = new LazyStateModelCache<String, String, String>(
                1, location -> "missing", () -> false);
        cache.defer("state", "model");

        assertEquals("missing", cache.get("state"));
        assertTrue(cache.isPending("state"));
    }
}
