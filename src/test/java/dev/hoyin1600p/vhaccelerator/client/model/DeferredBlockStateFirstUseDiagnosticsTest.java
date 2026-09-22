package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DeferredBlockStateFirstUseDiagnosticsTest {
    private static final long DELAY = DeferredBlockStateFirstUseDiagnostics.FINAL_SNAPSHOT_DELAY_NANOS;

    private ConcurrentDeferredModelRegistry<String, String> registry;

    private DeferredBlockStateFirstUseDiagnostics diagnostics() {
        Map<String, String> eager = new HashMap<>(Map.of("minecraft:missing", "missing"));
        registry = new ConcurrentDeferredModelRegistry<>(eager,
                List.of("a:stone#", "a:dirt#", "a:log#axis=y"),
                key -> key.startsWith("a:log") ? null : "baked(" + key + ")",
                "missing", (k, n, f, e) -> { });
        return new DeferredBlockStateFirstUseDiagnostics(registry);
    }

    @Test void firstFrameThenOneFinalSnapshotPerLevel() {
        var diagnostics = diagnostics();
        Object level = new Object();
        registry.get("a:stone#");
        diagnostics.recordBake("a:stone#", 2_000_000L);

        var first = diagnostics.observeFrame(level, 100L);
        assertNotNull(first);
        assertFalse(first.isFinal());
        assertEquals(3, first.selected());
        assertEquals(1, first.bakedOnDemand());
        assertEquals(2, first.unresolved());
        assertEquals(1, first.intervalBakes());
        assertEquals(2_000_000L, first.intervalNanos());
        assertEquals(List.of(new DeferredBlockStateFirstUseDiagnostics.SlowBake("a:stone#", 2_000_000L)),
                first.slowest());

        assertNull(diagnostics.observeFrame(level, 101L), "one first-frame snapshot per session");
        registry.get("a:log#axis=y");
        diagnostics.recordBake("a:log#axis=y", 3_000L);
        assertNull(diagnostics.observeFrame(level, 100L + DELAY - 1L));

        var last = diagnostics.observeFrame(level, 100L + DELAY + 7L);
        assertNotNull(last);
        assertTrue(last.isFinal());
        assertEquals(DELAY + 7L, last.sinceFirstFrameNanos());
        assertEquals(1, last.failed());
        assertEquals(1, last.intervalBakes(), "final covers only bakes after the first frame");
        assertEquals(3_000L, last.intervalNanos());
        assertTrue(last.describe().contains("a:log#axis=y 3 us"));

        assertNull(diagnostics.observeFrame(level, 100L + 3 * DELAY), "no duplicate final");
        assertFalse(diagnostics.finalPending());
    }

    @Test void dimensionChangeDropsPendingFinalAndStartsNewSession() {
        var diagnostics = diagnostics();
        Object overworld = new Object();
        Object vault = new Object();
        assertNotNull(diagnostics.observeFrame(overworld, 0L));
        diagnostics.recordBake("a:stone#", 10L);

        var first = diagnostics.observeFrame(vault, DELAY / 2);
        assertNotNull(first);
        assertFalse(first.isFinal(), "new level reports a first frame, not the stale final");
        assertEquals(1, first.intervalBakes());
        assertNull(diagnostics.observeFrame(vault, DELAY), "timer restarts with the new level");
        var last = diagnostics.observeFrame(vault, DELAY / 2 + DELAY);
        assertNotNull(last);
        assertTrue(last.isFinal());
        var back = diagnostics.observeFrame(overworld, 10 * DELAY);
        assertNotNull(back, "returning to a previous level instance is a new session");
        assertFalse(back.isFinal());
    }

    @Test void worldExitCancelsPendingFinalAndResetsInterval() {
        var diagnostics = diagnostics();
        Object level = new Object();
        assertNotNull(diagnostics.observeFrame(level, 0L));
        diagnostics.recordBake("a:stone#", 50L);
        diagnostics.cancelSession();
        assertFalse(diagnostics.finalPending());

        // Rejoin with the same level object still starts a fresh session.
        var rejoin = diagnostics.observeFrame(level, DELAY * 2);
        assertNotNull(rejoin);
        assertFalse(rejoin.isFinal());
        assertEquals(0, rejoin.intervalBakes(), "exit discards the previous interval");
        assertTrue(diagnostics.finalPending());
    }

    @Test void sessionsPerRegistryAreBounded() {
        var diagnostics = diagnostics();
        int logged = 0;
        for (int session = 0; session < DeferredBlockStateFirstUseDiagnostics.MAX_SESSIONS + 5; session++) {
            Object level = new Object();
            long start = session * 3 * DELAY;
            if (diagnostics.observeFrame(level, start) != null) {
                logged++;
            }
            if (diagnostics.observeFrame(level, start + DELAY) != null) {
                logged++;
            }
        }
        assertEquals(2 * DeferredBlockStateFirstUseDiagnostics.MAX_SESSIONS, logged);
        assertFalse(diagnostics.finalPending());
    }

    @Test void slowestBakesAreBoundedAndOrdered() {
        var diagnostics = diagnostics();
        for (int bake = 1; bake <= 20; bake++) {
            diagnostics.recordBake("k" + bake, bake * 1_000L);
        }
        var snapshot = diagnostics.observeFrame(new Object(), 0L);
        assertEquals(20, snapshot.intervalBakes());
        assertEquals(210_000L, snapshot.intervalNanos());
        assertEquals(DeferredBlockStateFirstUseDiagnostics.SLOWEST_LIMIT, snapshot.slowest().size());
        for (int index = 0; index < snapshot.slowest().size(); index++) {
            assertEquals("k" + (20 - index), snapshot.slowest().get(index).key());
        }
    }

    @Test void concurrentBakesAggregateExactly() throws Exception {
        var diagnostics = diagnostics();
        int threads = 8;
        int perThread = 5_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?>[] futures = new Future<?>[threads];
            for (int thread = 0; thread < threads; thread++) {
                int id = thread;
                futures[thread] = pool.submit(() -> {
                    start.await();
                    for (int bake = 0; bake < perThread; bake++) {
                        diagnostics.recordBake(id + ":" + bake, id * perThread + bake + 1L);
                    }
                    return null;
                });
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        long total = (long) threads * perThread;
        var snapshot = diagnostics.observeFrame(new Object(), 0L);
        assertEquals(total, snapshot.intervalBakes());
        assertEquals(total * (total + 1) / 2, snapshot.intervalNanos());
        assertEquals(total, snapshot.slowest().get(0).nanos());
        assertEquals((threads - 1) + ":" + (perThread - 1), snapshot.slowest().get(0).key());
        for (int index = 1; index < snapshot.slowest().size(); index++) {
            assertEquals(total - index, snapshot.slowest().get(index).nanos());
        }
    }
}
