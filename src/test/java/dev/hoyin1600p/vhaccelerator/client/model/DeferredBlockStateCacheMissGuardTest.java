package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DeferredBlockStateCacheMissGuardTest {
    private final Object bakery = new Object();
    private final Map<String, String> unbaked =
            new ConcurrentHashMap<>(Map.of("a:block/stone", "stone"));

    @Test void inactiveOutsideScope() {
        assertFalse(DeferredBlockStateCacheMissGuard.active(bakery));
        // No scope: a miss is left to the normal bakery path.
        assertDoesNotThrow(() -> DeferredBlockStateCacheMissGuard.check(
                bakery, unbaked, "a:block/absent"));
        assertDoesNotThrow(() -> DeferredBlockStateCacheMissGuard.check(
                bakery, unbaked, null));
    }

    @Test void cachedLookupPassesAndScopeClearsAfterSuccess() {
        String result = DeferredBlockStateCacheMissGuard.bake(bakery, "a:stone#", key -> {
            assertTrue(DeferredBlockStateCacheMissGuard.active(bakery));
            DeferredBlockStateCacheMissGuard.check(bakery, unbaked, "a:block/stone");
            return "baked(" + key + ")";
        });
        assertEquals("baked(a:stone#)", result);
        assertFalse(DeferredBlockStateCacheMissGuard.active(bakery));
    }

    @Test void missThrowsAndScopeClearsAfterFailure() {
        var miss = assertThrows(
                DeferredBlockStateCacheMissGuard.CacheMissException.class,
                () -> DeferredBlockStateCacheMissGuard.bake(bakery, "a:stone#", key -> {
                    DeferredBlockStateCacheMissGuard.check(bakery, unbaked, "a:block/absent");
                    return "unreachable";
                }));
        assertTrue(miss.getMessage().contains("a:block/absent"));
        assertEquals(0, miss.getStackTrace().length);
        assertFalse(DeferredBlockStateCacheMissGuard.active(bakery));

        assertThrows(IllegalStateException.class,
                () -> DeferredBlockStateCacheMissGuard.bake(bakery, "a:stone#", key -> {
                    throw new IllegalStateException("bake failed");
                }));
        assertFalse(DeferredBlockStateCacheMissGuard.active(bakery));
    }

    @Test void nullKeyInsideScopeIsAMiss() {
        assertThrows(DeferredBlockStateCacheMissGuard.CacheMissException.class,
                () -> DeferredBlockStateCacheMissGuard.bake(bakery, "a:stone#", key -> {
                    DeferredBlockStateCacheMissGuard.check(bakery, unbaked, null);
                    return "unreachable";
                }));
    }

    @Test void otherOwnerIsNotGuarded() {
        Object otherBakery = new Object();
        DeferredBlockStateCacheMissGuard.bake(bakery, "a:stone#", key -> {
            assertFalse(DeferredBlockStateCacheMissGuard.active(otherBakery));
            assertDoesNotThrow(() -> DeferredBlockStateCacheMissGuard.check(
                    otherBakery, unbaked, "a:block/absent"));
            return null;
        });
    }

    @Test void nestedScopesRestoreEnclosingScope() {
        Object inner = new Object();
        DeferredBlockStateCacheMissGuard.bake(bakery, "outer", outerKey -> {
            DeferredBlockStateCacheMissGuard.bake(inner, "inner", innerKey -> {
                assertTrue(DeferredBlockStateCacheMissGuard.active(inner));
                assertFalse(DeferredBlockStateCacheMissGuard.active(bakery));
                return null;
            });
            assertTrue(DeferredBlockStateCacheMissGuard.active(bakery));
            // A failed nested bake also restores the enclosing scope.
            assertThrows(DeferredBlockStateCacheMissGuard.CacheMissException.class,
                    () -> DeferredBlockStateCacheMissGuard.bake(bakery, "nested", key -> {
                        DeferredBlockStateCacheMissGuard.check(bakery, unbaked, "a:block/absent");
                        return null;
                    }));
            assertTrue(DeferredBlockStateCacheMissGuard.active(bakery));
            return null;
        });
        assertFalse(DeferredBlockStateCacheMissGuard.active(bakery));
        assertFalse(DeferredBlockStateCacheMissGuard.active(inner));
    }

    @Test void scopeIsIsolatedPerThread() throws Exception {
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch checked = new CountDownLatch(1);
            Future<?> guarded = other.submit(() -> DeferredBlockStateCacheMissGuard.bake(
                    bakery, "a:stone#", key -> {
                        entered.countDown();
                        await(checked);
                        assertTrue(DeferredBlockStateCacheMissGuard.active(bakery));
                        return null;
                    }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            // The other thread's live scope does not guard this thread.
            assertFalse(DeferredBlockStateCacheMissGuard.active(bakery));
            assertDoesNotThrow(() -> DeferredBlockStateCacheMissGuard.check(
                    bakery, unbaked, "a:block/absent"));
            checked.countDown();
            guarded.get(5, TimeUnit.SECONDS);
            // And the pooled thread's scope was cleared after its bake.
            assertFalse(other.submit(
                    () -> DeferredBlockStateCacheMissGuard.active(bakery)
            ).get(5, TimeUnit.SECONDS));
        } finally {
            other.shutdownNow();
        }
    }

    @Test void registryServesFallbackOnGuardedMiss() {
        List<String> failed = Collections.synchronizedList(new ArrayList<>());
        var registry = new ConcurrentDeferredModelRegistry<String, String>(
                new ConcurrentHashMap<>(Map.of("minecraft:missing", "missing")),
                List.of("a:stone#", "a:odd#"),
                location -> DeferredBlockStateCacheMissGuard.bake(bakery, location, key -> {
                    String dependency = key.equals("a:stone#") ? "a:block/stone" : "a:block/absent";
                    DeferredBlockStateCacheMissGuard.check(bakery, unbaked, dependency);
                    return "baked(" + key + ")";
                }),
                "missing",
                (key, nanos, didFail, failure) -> {
                    if (didFail) {
                        assertInstanceOf(
                                DeferredBlockStateCacheMissGuard.CacheMissException.class,
                                failure);
                        failed.add(key);
                    }
                });
        assertEquals("baked(a:stone#)", registry.get("a:stone#"));
        assertEquals("missing", registry.get("a:odd#"));
        assertEquals(List.of("a:odd#"), failed);
        assertFalse(DeferredBlockStateCacheMissGuard.active(bakery));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}

