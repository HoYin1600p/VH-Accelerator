package dev.hoyin1600p.vhaccelerator.compat.buildinggadgets;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
class CompactTemplateLookupDeferredTest {
    private static final String WORKER = "VHA BuildingGadgets template compaction";

    @BeforeEach @AfterEach void resetQueue() { CompactTemplateLookup.DeferredCompaction.reset(); }

    private static ImmutableMap<BlockPos, Object> map(int size) {
        ImmutableMap.Builder<BlockPos, Object> builder = ImmutableMap.builder();
        // Twenty equal-hash keys overflow a Guava bucket, which is what selects the JDK-backed layout.
        for (int i = 0; i < size; i++) {
            BlockPos key = i < 20 ? new BlockPos(961 * i, 0, 63 - i) : new BlockPos(i, 1000 + i % 13, 5000 + i / 13);
            builder.put(key, new String("value-" + i));
        }
        ImmutableMap<BlockPos, Object> map = builder.build();
        assertEquals("com.google.common.collect.JdkBackedImmutableMap", map.getClass().getName());
        return map;
    }

    private static Field field(ImmutableMap<BlockPos, ?> map) throws Exception {
        Field field = map.getClass().getDeclaredField("delegateMap");
        assertTrue(field.trySetAccessible());
        return field;
    }

    private static Object delegate(ImmutableMap<BlockPos, ?> map) throws Exception { return field(map).get(map); }

    private static void awaitTrue(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("timed out");
            Thread.sleep(2);
        }
    }

    @Test void smallMapsAreCompactedInline() throws Exception {
        ImmutableMap<BlockPos, Object> map = map(CompactTemplateLookup.SYNCHRONOUS_LIMIT);
        Object original = delegate(map);
        AtomicInteger installed = new AtomicInteger();
        assertTrue(CompactTemplateLookup.installDeferred(map, installed::incrementAndGet));
        assertNotSame(original, delegate(map));
        assertEquals(1, installed.get());
    }

    @Test void largeMapIsInstalledLaterAndReadersNeverSeeAnInconsistentLookup() throws Exception {
        int size = 200_000;
        ImmutableMap<BlockPos, Object> map = map(size);
        List<Map.Entry<BlockPos, Object>> entries = new ArrayList<>(map.entrySet());
        Object original = delegate(map);
        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger sweeps = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(4);
        List<Thread> readers = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            int offset = t * 31;
            Thread reader = new Thread(() -> {
                started.countDown();
                try {
                    while (!stop.get()) {
                        for (int i = 0; i < entries.size(); i += 7) {
                            Map.Entry<BlockPos, Object> entry = entries.get((i + offset) % entries.size());
                            if (map.get(entry.getKey()) != entry.getValue()) throw new AssertionError("wrong value");
                            if (map.get(new BlockPos(-1 - i, 0, 0)) != null) throw new AssertionError("phantom value");
                        }
                        sweeps.incrementAndGet();
                    }
                } catch (Throwable t2) {
                    failure.compareAndSet(null, t2);
                }
            });
            reader.setDaemon(true);
            readers.add(reader);
            reader.start();
        }
        started.await();
        AtomicInteger installed = new AtomicInteger();
        assertTrue(CompactTemplateLookup.installDeferred(map, installed::incrementAndGet));
        awaitTrue(() -> installed.get() == 1);
        int atInstall = sweeps.get();
        awaitTrue(() -> sweeps.get() >= atInstall + 8 || failure.get() != null);
        stop.set(true);
        for (Thread reader : readers) reader.join();
        assertNull(failure.get());
        assertNotSame(original, delegate(map));
        for (Map.Entry<BlockPos, Object> entry : entries) assertSame(entry.getValue(), map.get(entry.getKey()));
    }

    @Test void mismatchKeepsTheOriginalDelegate() throws Exception {
        ImmutableMap<BlockPos, Object> bad = map(CompactTemplateLookup.SYNCHRONOUS_LIMIT + 500);
        Field field = field(bad);
        @SuppressWarnings("unchecked")
        HashMap<BlockPos, Object> tampered = new HashMap<>((Map<BlockPos, Object>) field.get(bad));
        tampered.put(bad.keySet().iterator().next(), new String("different"));
        field.set(bad, tampered);
        ImmutableMap<BlockPos, Object> good = map(CompactTemplateLookup.SYNCHRONOUS_LIMIT + 500);
        AtomicInteger badInstalls = new AtomicInteger(), goodInstalls = new AtomicInteger();
        assertTrue(CompactTemplateLookup.installDeferred(bad, badInstalls::incrementAndGet));
        assertTrue(CompactTemplateLookup.installDeferred(good, goodInstalls::incrementAndGet));
        awaitTrue(() -> goodInstalls.get() == 1); // single worker, FIFO: bad has been processed by now
        assertEquals(0, badInstalls.get());
        assertSame(tampered, delegate(bad));
    }

    @Test void callerCostIsConstantWhileBuildAndVerifyAreNot() throws Exception {
        int size = 300_000;
        ImmutableMap<BlockPos, Object> synchronous = map(size), deferred = map(size);
        long start = System.nanoTime();
        assertTrue(CompactTemplateLookup.install(synchronous));
        long synchronousNanos = System.nanoTime() - start;
        Object original = delegate(deferred);
        AtomicInteger installed = new AtomicInteger();
        start = System.nanoTime();
        assertTrue(CompactTemplateLookup.installDeferred(deferred, installed::incrementAndGet));
        long callerNanos = System.nanoTime() - start;
        assertTrue(callerNanos * 10 < synchronousNanos, "caller " + callerNanos + " ns vs full install " + synchronousNanos + " ns");
        awaitTrue(() -> installed.get() == 1);
        assertNotSame(original, delegate(deferred));
    }

    @Test void shutdownWithPendingWorkReturnsPromptlyAndStopsTheWorker() throws Exception {
        List<ImmutableMap<BlockPos, Object>> maps = new ArrayList<>();
        for (int i = 0; i < 6; i++) maps.add(map(150_000));
        for (ImmutableMap<BlockPos, Object> map : maps) assertTrue(CompactTemplateLookup.installDeferred(map, null));
        long start = System.nanoTime();
        CompactTemplateLookup.shutdownDeferred();
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1));
        assertEquals(0, CompactTemplateLookup.DeferredCompaction.pending());
        assertFalse(CompactTemplateLookup.installDeferred(map(CompactTemplateLookup.SYNCHRONOUS_LIMIT + 1), null));
        awaitTrue(() -> Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.getName().equals(WORKER) && t.isAlive()));
        // Whatever finished before the stop is either the full original or the full verified index.
        for (ImmutableMap<BlockPos, Object> map : maps) {
            for (Map.Entry<BlockPos, Object> entry : map.entrySet()) {
                assertSame(entry.getValue(), map.get(entry.getKey()));
                break;
            }
            assertEquals(150_000, map.size());
        }
    }

    @Test void workerIsALowPriorityDaemon() throws Exception {
        ImmutableMap<BlockPos, Object> map = map(CompactTemplateLookup.SYNCHRONOUS_LIMIT + 1);
        AtomicInteger installed = new AtomicInteger();
        AtomicReference<Thread> thread = new AtomicReference<>();
        assertTrue(CompactTemplateLookup.installDeferred(map, () -> { thread.set(Thread.currentThread()); installed.incrementAndGet(); }));
        awaitTrue(() -> installed.get() == 1);
        assertTrue(thread.get().isDaemon());
        assertEquals(Thread.MIN_PRIORITY, thread.get().getPriority());
    }
}
