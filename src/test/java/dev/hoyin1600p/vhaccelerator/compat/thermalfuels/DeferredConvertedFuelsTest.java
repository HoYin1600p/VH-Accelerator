package dev.hoyin1600p.vhaccelerator.compat.thermalfuels;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Plays Thermal's manager contract (refresh = clear + createConvertedRecipes, reader = getConvertedFuels) with a
 * stand-in whose scan reads mutable "world" state, once eagerly and once through the deferral exactly as the mixins
 * wire it, and compares what a reader sees.
 */
class DeferredConvertedFuelsTest {
    /** The shape of StirlingFuelManager, with the three mixin hooks switchable. */
    static final class Manager {
        final boolean deferring;
        final DeferredConvertedFuels<String> deferred = new DeferredConvertedFuels<>();
        final List<String> convertedFuels = new ArrayList<>();
        final AtomicInteger scans = new AtomicInteger();
        /** Stands in for the registry, tags and other mods' burn-time handlers: changes between refreshes. */
        volatile int world;

        Manager(boolean deferring) {
            this.deferring = deferring;
        }

        void refresh(String recipeManager) {
            clear();
            createConvertedRecipes(recipeManager);
        }

        void clear() {
            if (deferring) deferred.discard();
            convertedFuels.clear();
        }

        void createConvertedRecipes(String recipeManager) {
            if (deferring && deferred.tryDefer(recipeManager)) return;
            scans.incrementAndGet();
            for (int item = 0; item < 3000; item++) {
                int energy = burnTime(item) * 10;
                if (energy >= 1000) convertedFuels.add("thermal:stirling_" + item + "=" + energy);
            }
        }

        int burnTime(int item) {
            return (item * 31 + world * 17) % 11 == 0 ? 100 + (item % 7) * 50 : 0;
        }

        List<String> getConvertedFuels() {
            if (deferring) {
                String owed = deferred.begin();
                if (owed != null) {
                    try {
                        createConvertedRecipes(owed);
                    } finally {
                        deferred.end();
                    }
                }
            }
            return convertedFuels;
        }
    }

    @Test
    void aReaderSeesTheListTheEagerRefreshBuilds() {
        Manager eager = new Manager(false);
        Manager lazy = new Manager(true);
        eager.refresh("rm");
        lazy.refresh("rm");
        assertEquals(0, lazy.scans.get(), "nothing reads the list, so nothing is scanned");
        assertFalse(eager.getConvertedFuels().isEmpty());
        assertEquals(eager.getConvertedFuels(), lazy.getConvertedFuels());
        assertEquals(1, lazy.scans.get());
        assertEquals(eager.getConvertedFuels(), lazy.getConvertedFuels());
        assertEquals(1, lazy.scans.get(), "a second read must not scan again");
    }

    @Test
    void eachRefreshDropsThePreviousOwedBuild() {
        Manager eager = new Manager(false);
        Manager lazy = new Manager(true);
        for (int world = 0; world < 5; world++) {
            eager.world = world;
            lazy.world = world;
            eager.refresh("rm" + world);
            lazy.refresh("rm" + world);
        }
        assertEquals(eager.getConvertedFuels(), lazy.getConvertedFuels());
        assertEquals(1, lazy.scans.get(), "five refreshes with no reader cost one scan in total");
        assertEquals(5, eager.scans.get());
    }

    @Test
    void aClearAfterARefreshDiscardsTheOwedBuild() {
        Manager eager = new Manager(false);
        Manager lazy = new Manager(true);
        eager.refresh("rm");
        lazy.refresh("rm");
        eager.clear();
        lazy.clear();
        assertEquals(List.of(), eager.getConvertedFuels());
        assertEquals(List.of(), lazy.getConvertedFuels());
        assertEquals(0, lazy.scans.get());
    }

    @Test
    void readsInterleavedWithRefreshesMatchTheEagerManagerOnRandomSequences() {
        for (long seed = 1; seed <= 200; seed++) {
            Random random = new Random(seed);
            Manager eager = new Manager(false);
            Manager lazy = new Manager(true);
            for (int step = 0; step < 40; step++) {
                switch (random.nextInt(4)) {
                    case 0 -> {
                        eager.world = lazy.world = random.nextInt(50);
                        eager.refresh("rm");
                        lazy.refresh("rm");
                    }
                    case 1 -> {
                        eager.clear();
                        lazy.clear();
                    }
                    default -> assertEquals(eager.getConvertedFuels(), lazy.getConvertedFuels(), "seed " + seed + " step " + step);
                }
            }
            assertEquals(eager.getConvertedFuels(), lazy.getConvertedFuels(), "seed " + seed);
            assertTrue(lazy.scans.get() <= eager.scans.get(), "seed " + seed);
        }
    }

    @Test
    void theOriginalScanRunsOnceInsideTheBuildAndIsNotDeferredAgain() {
        Manager lazy = new Manager(true);
        lazy.refresh("rm");
        assertTrue(lazy.deferred.isOwed());
        lazy.getConvertedFuels();
        assertFalse(lazy.deferred.isOwed(), "the build ran instead of re-deferring");
        assertEquals(1, lazy.scans.get());
    }

    @Test
    void concurrentFirstReadersScanOnceAndAllSeeTheFullList() throws Exception {
        Manager lazy = new Manager(true);
        lazy.refresh("rm");
        int readers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(readers);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> sizes = new ArrayList<>();
            for (int i = 0; i < readers; i++) {
                sizes.add(pool.submit(() -> {
                    start.await();
                    return lazy.getConvertedFuels().size();
                }));
            }
            start.countDown();
            Manager eager = new Manager(false);
            eager.refresh("rm");
            for (Future<Integer> size : sizes) assertEquals(eager.convertedFuels.size(), size.get());
            assertEquals(1, lazy.scans.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aFailingScanReleasesTheLockAndDoesNotRepeat() {
        DeferredConvertedFuels<String> deferred = new DeferredConvertedFuels<>();
        assertTrue(deferred.tryDefer("rm"));
        assertEquals("rm", deferred.begin());
        deferred.end();
        assertNull(deferred.begin(), "the owed build was taken exactly once");
        assertFalse(deferred.isOwed());
    }
}
