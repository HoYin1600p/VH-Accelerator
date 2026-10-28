package dev.hoyin1600p.vhaccelerator.backport.fix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Drives the exact sequence of the vanilla reload barrier callback (remove the
 * listener, then complete the future when the set is empty) from many threads
 * at once, using the set the mixin installs.
 */
class ReloadBarrierSetTest {
    private static final int THREADS = 32;
    private static final int LISTENERS = 512;
    private static final int ITERATIONS = 400;

    @Test
    void copyKeepsEveryElement() {
        List<Object> listeners = listeners(10);
        Set<Object> copy = ReloadBarrierSet.copyOf(listeners);
        assertEquals(10, copy.size());
        assertTrue(copy.containsAll(listeners));
    }

    @Test
    void completionAlwaysHappensExactlyOnceUnderContention() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                List<Object> listeners = listeners(LISTENERS);
                Set<Object> preparing = ReloadBarrierSet.copyOf(listeners);
                CompletableFuture<Object> allPreparations = new CompletableFuture<>();
                AtomicInteger dependentRuns = new AtomicInteger();
                AtomicInteger completions = new AtomicInteger();
                allPreparations.thenRun(dependentRuns::incrementAndGet);

                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> tasks = new ArrayList<>();
                for (int t = 0; t < THREADS; t++) {
                    int offset = t;
                    tasks.add(pool.submit(() -> {
                        start.await();
                        for (int i = offset; i < LISTENERS; i += THREADS) {
                            // The barrier callback, step for step.
                            preparing.remove(listeners.get(i));
                            if (preparing.isEmpty()
                                    && allPreparations.complete(Boolean.TRUE)) {
                                completions.incrementAndGet();
                            }
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> task : tasks) {
                    task.get(30, TimeUnit.SECONDS);
                }

                assertTrue(preparing.isEmpty(), "iteration " + iteration);
                assertTrue(allPreparations.isDone(), "hang at iteration " + iteration);
                assertEquals(1, completions.get(), "iteration " + iteration);
                assertEquals(1, dependentRuns.get(), "iteration " + iteration);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void doesNotCompleteWhileAnyListenerIsStillPreparing() {
        List<Object> listeners = listeners(3);
        Set<Object> preparing = ReloadBarrierSet.copyOf(listeners);
        CompletableFuture<Object> allPreparations = new CompletableFuture<>();
        for (int i = 0; i < 2; i++) {
            preparing.remove(listeners.get(i));
            if (preparing.isEmpty()) {
                allPreparations.complete(Boolean.TRUE);
            }
        }
        assertEquals(1, preparing.size());
        assertFalse(allPreparations.isDone());
        preparing.remove(listeners.get(2));
        if (preparing.isEmpty()) {
            allPreparations.complete(Boolean.TRUE);
        }
        assertTrue(allPreparations.isDone());
    }

    private static List<Object> listeners(int count) {
        List<Object> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Object());
        }
        return list;
    }
}
