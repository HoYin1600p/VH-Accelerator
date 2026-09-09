package dev.hoyin1600p.vhaccelerator.concurrent;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class StagedPreloadTest {
    @Test void cpuPreparationDoesNotHoldTheSerialDiskLane() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var io = Executors.newSingleThreadExecutor();
            var compute = Executors.newSingleThreadExecutor();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            try {
                var result = StagedPreload.start(() -> 7, value -> {
                    entered.countDown();
                    try { assertTrue(release.await(3, TimeUnit.SECONDS)); }
                    catch (InterruptedException e) { throw new AssertionError(e); }
                    return value + 1;
                }, io, compute, true);
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                assertEquals(42, CompletableFuture.supplyAsync(() -> 42, io).get(1, TimeUnit.SECONDS));
                assertFalse(result.isDone()); // Readiness still includes CPU preparation.
                release.countDown();
                assertEquals(8, result.join());
            } finally { release.countDown(); io.shutdownNow(); compute.shutdownNow(); }
        });
    }

    @Test void disabledPathRetainsOriginalExecutorAndFailuresPropagate() {
        Thread caller = Thread.currentThread();
        Executor direct = Runnable::run;
        Executor unused = task -> fail("disabled path must not use compute executor");
        assertEquals(3, StagedPreload.start(() -> 2, n -> {
            assertSame(caller, Thread.currentThread()); return n + 1;
        }, direct, unused, false).join());
        assertThrows(CompletionException.class, () -> StagedPreload.start(
                () -> { throw new IllegalStateException("read failed"); }, n -> n,
                direct, direct, true).join());
        assertNull(StagedPreload.start(() -> null, n -> n, direct, direct, true).join());
    }
}
