package dev.hoyin1600p.vhaccelerator.compat.targetdummy;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TargetDummySetupFixTest {
    @TempDir Path temp;

    @Test void configDefaultsSafeAndHonorsExplicitFalseBeforeForgeAttachment() throws Exception {
        Path config = temp.resolve("common.toml");
        assertTrue(TargetDummySetupFix.readEnabled(config));
        Files.writeString(config, "[compatibility]\ndeferTargetDummyDispenserRegistration = false\n");
        assertFalse(TargetDummySetupFix.readEnabled(config));
        Files.writeString(config, "[compatibility]\ndeferTargetDummyDispenserRegistration = true\n");
        assertTrue(TargetDummySetupFix.readEnabled(config));
    }

    @Test void doesNotWriteDuringParallelSetupAndPreservesOriginalTask() {
        List<Runnable> queue = new ArrayList<>();
        AtomicInteger writes = new AtomicInteger();
        Runnable registration = writes::incrementAndGet;
        TargetDummySetupFix.defer(queue::add, registration);
        assertEquals(0, writes.get());
        assertEquals(1, queue.size());
        assertSame(registration, queue.get(0));
        queue.forEach(Runnable::run);
        assertEquals(1, writes.get());
    }

    @Test void queueFailureNeverFallsBackToUnsafeImmediateRegistration() {
        AtomicInteger writes = new AtomicInteger();
        IllegalStateException expected = new IllegalStateException("queue unavailable");
        assertSame(expected, assertThrows(IllegalStateException.class, () ->
                TargetDummySetupFix.defer(task -> { throw expected; }, writes::incrementAndGet)));
        assertEquals(0, writes.get());
    }

    @Test void registrationFailureRemainsVisibleToTheQueue() {
        List<Runnable> queue = new ArrayList<>();
        IllegalStateException expected = new IllegalStateException("registration failed");
        TargetDummySetupFix.defer(queue::add, () -> { throw expected; });
        assertSame(expected, assertThrows(IllegalStateException.class, queue.get(0)::run));
    }

    @Test void realForgeQueueExecutesOnItsDrainingThreadNotTheSetupWorker() throws Exception {
        var queue = new net.minecraftforge.fml.DeferredWorkQueue(
                net.minecraftforge.fml.ModLoadingStage.COMMON_SETUP);
        var executedOn = new java.util.concurrent.atomic.AtomicReference<Thread>();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> TargetDummySetupFix.defer(
                    task -> queue.enqueueWork(null, task),
                    () -> executedOn.set(Thread.currentThread()))).get(5, TimeUnit.SECONDS);
            assertNull(executedOn.get());
            queue.runTasks();
            assertSame(Thread.currentThread(), executedOn.get());
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void forcedOverlappingSetupCannotOverlapTheDeferredMapWrite() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch otherWriterEntered = new CountDownLatch(1);
        CountDownLatch allowOtherWriterExit = new CountDownLatch(1);
        ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
        AtomicInteger activeWriters = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        try {
            Future<?> otherMod = workers.submit(() -> {
                activeWriters.incrementAndGet();
                otherWriterEntered.countDown();
                try { assertTrue(allowOtherWriterExit.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                finally { activeWriters.decrementAndGet(); }
            });
            assertTrue(otherWriterEntered.await(5, TimeUnit.SECONDS));
            Runnable registration = () -> {
                assertEquals(0, activeWriters.get(), "shared map write overlaps another mod");
                writes.incrementAndGet();
            };
            // Negative control proves this schedule detects the original immediate-write defect.
            assertThrows(AssertionError.class, registration::run);
            workers.submit(() -> TargetDummySetupFix.defer(queue::add, registration)).get(5, TimeUnit.SECONDS);
            assertEquals(0, writes.get());
            allowOtherWriterExit.countDown();
            otherMod.get(5, TimeUnit.SECONDS);
            queue.forEach(Runnable::run);
            assertEquals(1, writes.get());
        } finally {
            allowOtherWriterExit.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
