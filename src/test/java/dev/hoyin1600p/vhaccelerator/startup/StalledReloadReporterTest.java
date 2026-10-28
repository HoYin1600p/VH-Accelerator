package dev.hoyin1600p.vhaccelerator.startup;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StalledReloadReporterTest {
    static final class NeverReachesBarrierListener { }

    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
    }

    @Test
    void namesListenerWhosePrepareNeverFinishes() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();
        StalledReloadReporter reporter = new StalledReloadReporter(20, TimeUnit.MILLISECONDS, 10, System::nanoTime, logs::add);
        reporter.reloadStarted(7L);
        String done = reporter.prepareStarted("fast.Owner", "fast.Listener", 7L);
        String stuck = reporter.prepareStarted("net.example.RecipeManager", NeverReachesBarrierListener.class.getName(), 7L);
        String applying = reporter.prepareStarted("slow.Apply", "slow.ApplyListener", 7L);
        reporter.prepareFinished(applying);
        reporter.listenerFinished(done);
        await(() -> !logs.isEmpty());
        reporter.reloadFinished();
        assertFalse(logs.isEmpty());
        String block = logs.get(0);
        System.out.println(block);
        assertTrue(block.contains(NeverReachesBarrierListener.class.getName()), block);
        assertTrue(block.contains("net.example.RecipeManager"), block);
        assertTrue(block.contains("generation 7"), block);
        assertTrue(block.contains("prepare open"), block);
        assertTrue(block.contains("slow.ApplyListener") && block.contains("apply open"), block);
        assertFalse(block.contains("fast.Listener"), block);
        assertNotNull(stuck);
    }

    @Test
    void finishedListenersAreNotReported() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();
        StalledReloadReporter reporter = new StalledReloadReporter(10, TimeUnit.MILLISECONDS, 10, System::nanoTime, logs::add);
        reporter.reloadStarted(1L);
        reporter.listenerFinished(reporter.prepareStarted("a", "a.Listener", 1L));
        Thread.sleep(100);
        reporter.reloadFinished();
        assertTrue(logs.isEmpty(), logs.toString());
    }

    @Test
    void checksStopAfterCompletion() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();
        StalledReloadReporter reporter = new StalledReloadReporter(10, TimeUnit.MILLISECONDS, 10, System::nanoTime, logs::add);
        reporter.reloadStarted(1L);
        reporter.prepareStarted("a", "a.Listener", 1L);
        await(() -> !logs.isEmpty());
        reporter.reloadFinished();
        assertFalse(reporter.scheduled());
        int count = logs.size();
        Thread.sleep(100);
        assertEquals(count, logs.size());
    }

    @Test
    void checksStopAfterMaximum() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();
        StalledReloadReporter reporter = new StalledReloadReporter(5, TimeUnit.MILLISECONDS, 3, System::nanoTime, logs::add);
        reporter.reloadStarted(1L);
        reporter.prepareStarted("a", "a.Listener", 1L);
        await(() -> logs.size() >= 3);
        Thread.sleep(100);
        reporter.reloadFinished();
        assertEquals(3, logs.size());
    }

    @Test
    void failuresInsideReporterAreContained() throws Exception {
        StalledReloadReporter throwing = new StalledReloadReporter(10, TimeUnit.MILLISECONDS, 10, System::nanoTime,
                message -> { throw new IllegalStateException("log backend down"); });
        assertDoesNotThrow(() -> {
            throwing.reloadStarted(1L);
            throwing.prepareStarted("a", "a.Listener", 1L);
            Thread.sleep(80);
            assertTrue(throwing.scheduled(), "schedule must survive a failing check");
            throwing.reloadFinished();
        });
        StalledReloadReporter badClock = new StalledReloadReporter(10, TimeUnit.MILLISECONDS, 10,
                () -> { throw new AssertionError("clock"); }, message -> { });
        assertDoesNotThrow(() -> {
            assertNull(badClock.prepareStarted("a", "a.Listener", 1L));
            badClock.prepareFinished(null);
            badClock.listenerFinished(null);
            badClock.reloadFinished();
        });
    }
}
