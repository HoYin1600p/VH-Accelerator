package dev.hoyin1600p.vhaccelerator.compat.smoothboot;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SmoothBootThreadPrioritiesTest {
    @TempDir Path temp;

    @Test void configDefaultsOnAndHonorsExplicitFalseBeforeForgeAttachment() throws Exception {
        Path config = temp.resolve("common.toml");
        assertTrue(SmoothBootThreadPriorities.readEnabled(config));
        Files.writeString(config, "[compatibility]\nrestoreSmoothBootThreadPriorities = false\n");
        assertFalse(SmoothBootThreadPriorities.readEnabled(config));
        Files.writeString(config, "[compatibility]\nrestoreSmoothBootThreadPriorities = true\n");
        assertTrue(SmoothBootThreadPriorities.readEnabled(config));
    }

    @Test void cachedIoPoolKeepsItsInstanceAndRaisesFutureThreads() throws Exception {
        AtomicInteger created = new AtomicInteger();
        ThreadFactory lowest = task -> {
            Thread thread = new Thread(task, "IO-Worker-test-" + created.incrementAndGet());
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        };
        ExecutorService pool = Executors.newCachedThreadPool(lowest);
        try {
            assertSame(pool, SmoothBootThreadPriorities.restore("IO", pool));
            assertSame(pool, SmoothBootThreadPriorities.restore("IO", pool));
            int priority = pool.submit(() -> Thread.currentThread().getPriority()).get(5, TimeUnit.SECONDS);
            assertEquals(Thread.NORM_PRIORITY, priority);
            assertEquals(1, created.get(), "Smooth Boot's own factory still creates the thread");
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void forkJoinPoolIsTwinnedOnceWithSameShapeAndNormalPriority() throws Exception {
        AtomicInteger created = new AtomicInteger();
        Thread.UncaughtExceptionHandler handler = (thread, failure) -> { };
        ForkJoinPool.ForkJoinWorkerThreadFactory lowest = pool -> {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setName("Worker-Main-test-" + created.incrementAndGet());
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        };
        ForkJoinPool original = new ForkJoinPool(3, lowest, handler, true);
        ExecutorService restored = SmoothBootThreadPriorities.restore("Main", original);
        try {
            assertNotSame(original, restored);
            ForkJoinPool twin = assertInstanceOf(ForkJoinPool.class, restored);
            assertEquals(3, twin.getParallelism());
            assertTrue(twin.getAsyncMode());
            assertSame(handler, twin.getUncaughtExceptionHandler());
            assertSame(twin, SmoothBootThreadPriorities.restore("Main", original), "one twin per pool");
            assertSame(twin, SmoothBootThreadPriorities.restore("Main", twin), "twin is already covered");
            String name = twin.submit(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS);
            int priority = twin.submit(() -> Thread.currentThread().getPriority()).get(5, TimeUnit.SECONDS);
            assertTrue(name.startsWith("Worker-Main-test-"), name);
            assertEquals(Thread.NORM_PRIORITY, priority);
            assertEquals(0, original.getPoolSize(), "the replaced pool never starts a worker");
        } finally {
            twinShutdown(restored);
            twinShutdown(original);
        }
    }

    @Test void raiseNeverLowersAndLeavesUnknownExecutorsAlone() {
        Thread high = new Thread(() -> { });
        high.setPriority(7);
        assertSame(high, SmoothBootThreadPriorities.raise("test", high));
        assertEquals(7, high.getPriority());
        ExecutorService direct = new AbstractExecutorService() {
            @Override public void shutdown() { }
            @Override public java.util.List<Runnable> shutdownNow() { return java.util.List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
            @Override public void execute(Runnable command) { command.run(); }
        };
        assertSame(direct, SmoothBootThreadPriorities.restore("Bootstrap", direct));
    }

    private static void twinShutdown(ExecutorService pool) throws InterruptedException {
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }
}
