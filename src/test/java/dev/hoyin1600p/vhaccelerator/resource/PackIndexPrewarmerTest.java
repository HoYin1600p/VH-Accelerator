package dev.hoyin1600p.vhaccelerator.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class PackIndexPrewarmerTest {
    @Test
    void drainSkipsTheQueryingPackAndDeadReferences() {
        ConcurrentLinkedQueue<WeakReference<PackIndexPrewarmer.Source>> queue =
                new ConcurrentLinkedQueue<>();
        PackIndexPrewarmer.Source current = () -> null;
        PackIndexPrewarmer.Source other = () -> null;
        PackIndexPrewarmer.Source third = () -> null;
        queue.add(new WeakReference<>(other));
        queue.add(new WeakReference<>(current));
        queue.add(new WeakReference<>(null));
        queue.add(new WeakReference<>(third));
        List<PackIndexPrewarmer.Source> drained = PackIndexPrewarmer.drain(queue, current);
        assertEquals(List.of(other, third), drained, "registration order, without the current pack");
        assertTrue(queue.isEmpty());
    }

    @Test
    void kickHandsPacksToWorkersWithoutWaitingForThem() throws Exception {
        assumeTrue(SharedWorkers.budget().compute() >= 2, "needs the shared compute pool");
        while (PackIndexPrewarmer.pendingForTesting() > 0) {
            PackIndexPrewarmer.kick(null);
        }
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger finished = new AtomicInteger();
        var workers = java.util.concurrent.ConcurrentHashMap.<Thread>newKeySet();
        List<PackIndexPrewarmer.Source> packs = new ArrayList<>();
        for (int pack = 0; pack < 3; pack++) {
            PackIndexPrewarmer.Source source = () -> {
                workers.add(Thread.currentThread());
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                finished.incrementAndGet();
                return null;
            };
            packs.add(source);
            PackIndexPrewarmer.register(source);
        }
        long begin = System.nanoTime();
        PackIndexPrewarmer.kick(packs.get(0));
        long elapsedMillis = (System.nanoTime() - begin) / 1_000_000L;
        // Packs 1 and 2 are running (blocked) on workers; the kick itself returned at once.
        assertTrue(elapsedMillis < 2_000, "kick must not wait for builds, took " + elapsedMillis);
        assertEquals(0, finished.get());
        release.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (finished.get() < 2 && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertEquals(2, finished.get(), "every registered pack but the querying one is prewarmed");
        assertTrue(workers.stream().allMatch(Thread::isDaemon), "pack-index workers must not retain a failed JVM");
        assertTrue(workers.stream().allMatch(t -> t.getName().startsWith("VH Accelerator pack index-")));
        assertEquals(0, PackIndexPrewarmer.pendingForTesting());
    }
}
