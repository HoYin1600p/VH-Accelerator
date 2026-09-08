package dev.hoyin1600p.vhaccelerator.concurrent;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SharedWorkersTest {
    @Test void hardwareBudgetUsesAllAvailableProcessorsAcrossSharedLanes() {
        for (int processors = 0; processors <= 256; processors++) {
            WorkerBudget budget = WorkerBudget.forProcessors(processors);
            assertEquals(processors, budget.total());
            assertEquals(budget.total(), budget.compute() + budget.io());
            assertEquals(processors == 0 ? 0 : 1, budget.io());
        }
    }

    @Test void tinyCpuRunsWithoutAdditionalComputeThreads() {
        for (int processors : new int[]{0, 1}) {
            try (var lanes = new SharedWorkers.Lanes(WorkerBudget.forProcessors(processors))) {
                Thread caller = Thread.currentThread();
                assertSame(caller, lanes.invoke(Thread::currentThread));
                lanes.forEach(List.of(1, 2), ignored -> assertSame(caller, Thread.currentThread()));
            }
        }
    }

    @Test void nestedBatchesCompleteWithOnlyOneComputeWorker() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (var lanes = new SharedWorkers.Lanes(WorkerBudget.forProcessors(2))) {
                AtomicInteger sum = new AtomicInteger();
                lanes.invoke(() -> {
                    lanes.forEach(List.of(1, 2, 3), outer ->
                            lanes.forEach(List.of(1, 2), inner -> sum.addAndGet(outer * inner)));
                    return null;
                });
                assertEquals(18, sum.get());
                // The stateful-builder API also permits nested asynchronous
                // submissions without a compensation worker or self-deadlock.
                assertEquals(42, lanes.invoke(() -> CompletableFuture.supplyAsync(
                        () -> 42, lanes.computeExecutor()).join()));
            }
        });
    }

    @Test void parallelStreamsStayInBoundedPoolIncludingSingleWorker() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            for (int processors : new int[]{2, 8, 16}) {
                WorkerBudget budget = WorkerBudget.forProcessors(processors);
                try (var lanes = new SharedWorkers.Lanes(budget)) {
                    var threads = ConcurrentHashMap.<Thread>newKeySet();
                    lanes.invoke(() -> IntStream.range(0, 10_000).parallel().map(i -> {
                        assertTrue(lanes.isComputeWorker());
                        threads.add(Thread.currentThread());
                        return i;
                    }).sum());
                    assertTrue(threads.size() <= budget.compute());
                    assertTrue(threads.stream().allMatch(t -> t.getContextClassLoader() == SharedWorkers.class.getClassLoader()));
                }
            }
        });
    }

    @Test void ioCanCompleteWhileComputeWaitsAndWritesStayOrdered() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (var lanes = new SharedWorkers.Lanes(WorkerBudget.forProcessors(4))) {
                assertEquals(7, lanes.invoke(() -> CompletableFuture.supplyAsync(
                        () -> 7, lanes.ioExecutor()).join()));
                var order = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
                CompletableFuture<?>[] jobs = IntStream.range(0, 100).mapToObj(i ->
                        CompletableFuture.runAsync(() -> order.add(i), lanes.ioExecutor()))
                        .toArray(CompletableFuture[]::new);
                CompletableFuture.allOf(jobs).join();
                assertEquals(IntStream.range(0, 100).boxed().toList(), order);
            }
        });
    }

    @Test void failureStillWaitsForOtherTasksBeforeFallback() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (var lanes = new SharedWorkers.Lanes(WorkerBudget.forProcessors(6))) {
                CountDownLatch started = new CountDownLatch(1);
                AtomicInteger finished = new AtomicInteger();
                assertThrows(IllegalStateException.class, () -> lanes.forEach(List.of(0, 1), i -> {
                    if (i == 0) {
                        try { assertTrue(started.await(2, TimeUnit.SECONDS)); }
                        catch (InterruptedException e) { throw new AssertionError(e); }
                        throw new IllegalStateException("test failure");
                    }
                    started.countDown();
                    finished.incrementAndGet();
                }));
                assertEquals(1, finished.get());
            }
        });
    }

    @Test void overlappingFeaturesShareOneTotalThreadLimit() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            WorkerBudget budget = WorkerBudget.forProcessors(8);
            try (var lanes = new SharedWorkers.Lanes(budget)) {
                var threads = ConcurrentHashMap.<Thread>newKeySet();
                CountDownLatch started = new CountDownLatch(budget.total());
                CountDownLatch release = new CountDownLatch(1);
                Runnable work = () -> {
                    threads.add(Thread.currentThread());
                    started.countDown();
                    try { assertTrue(release.await(3, TimeUnit.SECONDS)); }
                    catch (InterruptedException e) { throw new AssertionError(e); }
                };
                var tasks = new java.util.ArrayList<CompletableFuture<Void>>();
                for (int i = 0; i < budget.compute(); i++) {
                    tasks.add(CompletableFuture.runAsync(work, lanes.computeExecutor()));
                }
                tasks.add(CompletableFuture.runAsync(work, lanes.ioExecutor()));
                try { assertTrue(started.await(3, TimeUnit.SECONDS)); }
                finally { release.countDown(); }
                CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
                assertEquals(budget.total(), threads.size());
            }
        });
    }
}
