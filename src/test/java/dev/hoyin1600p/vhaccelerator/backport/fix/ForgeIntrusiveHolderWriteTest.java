package dev.hoyin1600p.vhaccelerator.backport.fix;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.core.Holder;
import org.junit.jupiter.api.Test;

class ForgeIntrusiveHolderWriteTest {
    @Test void originalConcurrentCreationCanLeaveANullHolderAfterBothWritersFinish() throws Exception { race(false); }
    @Test void productionRegistryMonitorClosesTheSameNullHolderWindow() throws Exception { race(true); }

    private static void race(boolean locked) throws Exception {
        Map<Object, Object> map = IdentityMapRaceOracle.newMap();
        var active = new IdentityMapRaceOracle.Scenario(map);
        IdentityMapRaceOracle.scenario = active;
        int capacity = IdentityMapRaceOracle.capacity(map);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Object> created = new AtomicReference<>();
        CountDownLatch resizing = new CountDownLatch(1), resized = new CountDownLatch(1);
        Thread first = daemon(() -> created.set(compute(active, active.key, locked)), failure);
        Thread second = daemon(() -> {
            resizing.countDown();
            for (int i = 0; i < 32 && capacity(map) == capacity; i++) compute(active, new Object(), locked);
            assertNotEquals(capacity, capacity(map), "must exercise an actual JDK resize");
            resized.countDown();
        }, failure);
        try {
            first.start();
            assertTrue(active.keyStored.await(2, TimeUnit.SECONDS));
            second.start();
            assertTrue(resizing.await(2, TimeUnit.SECONDS));
            assertEquals(locked, active.heldRegistryMonitor);
            if (locked) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (second.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline
                        && failure.get() == null) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                assertEquals(Thread.State.BLOCKED, second.getState(), "contender must wait on the registry monitor");
                assertEquals(1, resized.getCount());
            } else {
                assertTrue(resized.await(2, TimeUnit.SECONDS));
            }
        } finally {
            active.release.countDown();
            first.join(2000);
            if (second.getState() != Thread.State.NEW) second.join(2000);
            IdentityMapRaceOracle.scenario = null;
        }
        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertNull(failure.get(), () -> "oracle worker failed: " + failure.get());
        assertNotNull(created.get());
        assertTrue(map.containsKey(active.key));
        if (locked) {
            assertSame(created.get(), map.get(active.key));
            assertTrue(map.values().stream().noneMatch(java.util.Objects::isNull));
            assertDoesNotThrow(() -> forgeFreezePredicate(map));
        } else {
            assertNull(map.get(active.key), "resize copied the key before its holder value was published");
            assertThrows(NullPointerException.class, () -> forgeFreezePredicate(map));
            assertThrows(NullPointerException.class, () -> {
                for (Object value : map.values()) ((Holder.Reference<?>) value).getType();
            });
        }
    }

    private static Object compute(IdentityMapRaceOracle.Scenario active, Object key, boolean locked) {
        return locked ? RegistryWriteLock.computeHolder(active.registry, active.map, key,
                k -> Holder.Reference.createIntrusive(null, k))
                : active.map.computeIfAbsent(key, k -> Holder.Reference.createIntrusive(null, k));
    }

    private static void forgeFreezePredicate(Map<Object, Object> holders) {
        holders.values().stream().filter(value -> {
            Holder.Reference<?> holder = (Holder.Reference<?>) value;
            return holder.getType() == Holder.Reference.Type.INTRUSIVE && !holder.isBound();
        }).toList();
    }

    private static int capacity(Map<?, ?> map) {
        try { return IdentityMapRaceOracle.capacity(map); }
        catch (Exception e) { throw new AssertionError(e); }
    }

    private static Thread daemon(Runnable work, AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> { try { work.run(); } catch (Throwable t) { failure.compareAndSet(null, t); } },
                "intrusive holder test");
        thread.setDaemon(true);
        return thread;
    }

    @Test void sameRegistrySerializesFactoriesAndPreservesIdentity() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            Object registry = new Object(), key = new Object();
            Map<Object, Object> map = new IdentityHashMap<>();
            AtomicInteger calls = new AtomicInteger();
            Object first = RegistryWriteLock.computeHolder(registry, map, key, ignored -> {
                assertTrue(Thread.holdsLock(registry)); calls.incrementAndGet(); return new Object();
            });
            assertSame(first, RegistryWriteLock.computeHolder(registry, map, key, ignored -> {
                throw new AssertionError("existing holder factory must not run again");
            }));
            assertEquals(1, calls.get());
        });
    }

    @Test void nullKeysNullResultsAndFactoryFailuresKeepOriginalMapSemantics() {
        Object registry = new Object(), value = new Object();
        Map<Object, Object> map = new IdentityHashMap<>();
        assertSame(value, RegistryWriteLock.computeHolder(registry, map, null, key -> value));
        Object absent = new Object();
        assertNull(RegistryWriteLock.computeHolder(registry, map, absent, key -> null));
        assertFalse(map.containsKey(absent));
        RuntimeException expected = new IllegalStateException("factory failed");
        assertSame(expected, assertThrows(RuntimeException.class,
                () -> RegistryWriteLock.computeHolder(registry, map, absent, key -> { throw expected; })));
        assertFalse(Thread.holdsLock(registry));
        assertFalse(map.containsKey(absent));
    }
}
