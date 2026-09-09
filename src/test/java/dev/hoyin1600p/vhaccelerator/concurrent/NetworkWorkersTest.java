package dev.hoyin1600p.vhaccelerator.concurrent;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class NetworkWorkersTest {
    @Test void blockedNetworkDoesNotOccupyComputeOrDiskLanes() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (var network = new NetworkWorkers(32);
                 var other = new SharedWorkers.Lanes(WorkerBudget.forProcessors(2))) {
                var started = new CountDownLatch(2);
                var release = new CountDownLatch(1);
                Runnable blocking = () -> {
                    assertTrue(Thread.currentThread().getName().startsWith("VH Accelerator network-"));
                    assertSame(NetworkWorkers.class.getClassLoader(), Thread.currentThread().getContextClassLoader());
                    started.countDown();
                    try { assertTrue(release.await(3, TimeUnit.SECONDS)); }
                    catch (InterruptedException e) { throw new AssertionError(e); }
                };
                var first = CompletableFuture.runAsync(blocking, network.executor());
                var second = CompletableFuture.runAsync(blocking, network.executor());
                try {
                    assertTrue(started.await(2, TimeUnit.SECONDS));
                    assertEquals(7, CompletableFuture.supplyAsync(() -> 7, other.computeExecutor()).get(1, TimeUnit.SECONDS));
                    assertEquals(8, CompletableFuture.supplyAsync(() -> 8, other.ioExecutor()).get(1, TimeUnit.SECONDS));
                } finally { release.countDown(); }
                CompletableFuture.allOf(first, second).join();
            }
        });
    }
}
