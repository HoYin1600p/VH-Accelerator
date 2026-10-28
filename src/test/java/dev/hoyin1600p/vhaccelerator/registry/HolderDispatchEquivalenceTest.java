package dev.hoyin1600p.vhaccelerator.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * Runs Forge's full loop and the indexed loop over twin handler sets and requires the same sequence
 * of handler applications, field writes (log entries) and collected exceptions per event.
 */
class HolderDispatchEquivalenceTest {
    /** Handler base: deterministic hash so twin HashSets iterate in the same order. */
    abstract static class Handler implements Consumer<Predicate<String>> {
        final int id;
        final List<String> log;

        Handler(int id, List<String> log) {
            this.id = id;
            this.log = log;
        }

        @Override
        public int hashCode() {
            return id * 0x9E3779B1;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Handler h && h.id == id && h.getClass() == getClass();
        }
    }

    /** Like ObjectHolderRef: acts only for its key. */
    static final class Keyed extends Handler {
        final String key;

        Keyed(int id, List<String> log, String key) {
            super(id, log);
            this.key = key;
        }

        @Override
        public void accept(Predicate<String> filter) {
            if (filter.test(key)) {
                log.add("write " + id + " " + key);
            }
        }
    }

    /** Like RegistryObject$2: one-time registry check that may throw, then acts only for its key. */
    static final class Lazy extends Handler {
        final String key;
        final boolean optional;
        final boolean registryMissing;
        boolean exists;
        boolean invalid;

        Lazy(int id, List<String> log, String key, boolean optional, boolean registryMissing) {
            super(id, log);
            this.key = key;
            this.optional = optional;
            this.registryMissing = registryMissing;
        }

        @Override
        public void accept(Predicate<String> filter) {
            if (invalid) {
                return;
            }
            if (!optional && !exists) {
                if (registryMissing) {
                    invalid = true;
                    throw new IllegalStateException("missing registry for " + id);
                }
                exists = true;
                log.add("exists " + id);
            }
            if (filter.test(key)) {
                log.add("write " + id + " " + key);
            }
        }
    }

    /** A handler nothing is known about (lambda handlers, mod handlers): runs on every event. */
    static final class Opaque extends Handler {
        final boolean throwsOnKey;
        final String key;

        Opaque(int id, List<String> log, String key, boolean throwsOnKey) {
            super(id, log);
            this.key = key;
            this.throwsOnKey = throwsOnKey;
        }

        @Override
        public void accept(Predicate<String> filter) {
            log.add("visit " + id);
            if (throwsOnKey && filter.test(key)) {
                throw new IllegalArgumentException("opaque failure " + id);
            }
        }
    }

    static Object classify(Object h) {
        if (h instanceof Keyed k) {
            return k.key;
        }
        if (h instanceof Lazy l) {
            return l.invalid || l.exists || l.optional ? l.key : HolderDispatchIndex.UNSETTLED;
        }
        return HolderDispatchIndex.ALWAYS;
    }

    /** One side of the comparison: a handler set, its log and the way events are run. */
    static final class Side {
        final List<String> log = new ArrayList<>();
        final Set<Handler> set = new HashSet<>();
        final Object lock = new Object();
        final HolderDispatch<Handler> engine;
        final boolean indexed;

        Side(boolean indexed, int maxRebuilds) {
            this.indexed = indexed;
            this.engine = new HolderDispatch<>(HolderDispatchEquivalenceTest::classify, maxRebuilds);
        }

        void add(Handler h) {
            set.add(h);
            engine.mutated();
        }

        void remove(Handler h) {
            set.remove(h);
            engine.mutated();
        }

        /** Mirrors ObjectHolderRegistry.applyObjectHolders(filter): collect failures, keep going. */
        List<String> event(String key) {
            List<String> failures = new ArrayList<>();
            Predicate<String> filter = key::equals;
            Consumer<Handler> body = h -> {
                try {
                    h.accept(filter);
                } catch (Exception e) {
                    failures.add(e.getMessage());
                }
            };
            if (!indexed || !engine.dispatch(set, lock, key, body)) {
                set.forEach(body);
            }
            return failures;
        }
    }

    private static void populate(Side a, Side b, Random random, int count, String[] keys, int firstId) {
        List<Handler> pairA = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int id = firstId + i;
            String key = keys[random.nextInt(keys.length)];
            int kind = random.nextInt(100);
            if (kind < 55) {
                a.add(new Keyed(id, a.log, key));
                b.add(new Keyed(id, b.log, key));
            } else if (kind < 93) {
                boolean optional = random.nextInt(5) == 0;
                boolean missing = random.nextInt(25) == 0;
                a.add(new Lazy(id, a.log, key, optional, missing));
                b.add(new Lazy(id, b.log, key, optional, missing));
            } else {
                boolean throwsOnKey = random.nextBoolean();
                a.add(new Opaque(id, a.log, key, throwsOnKey));
                b.add(new Opaque(id, b.log, key, throwsOnKey));
            }
        }
    }

    private static void runScenario(long seed, int handlers, int events, int registries, int maxRebuilds) {
        Random random = new Random(seed);
        String[] keys = new String[registries];
        for (int i = 0; i < registries; i++) {
            keys[i] = "mod:registry_" + i;
        }
        Side original = new Side(false, maxRebuilds);
        Side indexed = new Side(true, maxRebuilds);
        populate(original, indexed, random, handlers, keys, 0);
        int nextId = handlers;
        for (int e = 0; e < events; e++) {
            String key = random.nextInt(10) == 0 ? "mod:unknown_" + random.nextInt(3) : keys[random.nextInt(registries)];
            List<String> expected = original.event(key);
            List<String> actual = indexed.event(key);
            assertEquals(expected, actual, "exceptions, seed " + seed + " event " + e);
            assertEquals(original.log, indexed.log, "applications, seed " + seed + " event " + e);
            int change = random.nextInt(8);
            if (change == 0) {
                populate(original, indexed, random, 1 + random.nextInt(20), keys, nextId);
                nextId += 40;
            } else if (change == 1 && !original.set.isEmpty()) {
                int victim = original.set.iterator().next().id;
                Handler a = original.set.stream().filter(h -> h.id == victim).findFirst().orElseThrow();
                Handler b = indexed.set.stream().filter(h -> h.id == victim).findFirst().orElseThrow();
                original.remove(a);
                indexed.remove(b);
            }
        }
        assertEquals(original.log, indexed.log);
    }

    @Test
    void manySmallRandomScenariosApplyTheSameHandlersInTheSameOrder() {
        for (long seed = 0; seed < 300; seed++) {
            runScenario(seed, 1 + (int) (seed % 60), 40, 1 + (int) (seed % 7), 8);
        }
    }

    @Test
    void aSwitchedOffDispatcherStillMatchesTheOriginalLoop() {
        for (long seed = 1000; seed < 1040; seed++) {
            runScenario(seed, 40, 60, 5, 0);
        }
    }

    @Test
    void aLargeRealisticSetMatchesAndVisitsFarFewerHandlers() {
        // Registry sizes follow the level.dat snapshot of the profiled Vault Hunters server (59,735 entries).
        int[] sizes = {29144, 26739, 2141, 662, 237, 183, 159, 87, 70, 70, 47, 30, 29, 29, 28, 19, 17, 11, 9, 7, 5, 4, 3, 2, 2, 1};
        String[] keys = new String[sizes.length];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = "registry_" + i;
        }
        Side original = new Side(false, 8);
        Side indexed = new Side(true, 8);
        Random random = new Random(42);
        int id = 0;
        for (int r = 0; r < sizes.length; r++) {
            for (int i = 0; i < sizes[r]; i++, id++) {
                int kind = random.nextInt(1000);
                if (kind < 700) {
                    boolean optional = random.nextInt(10) == 0;
                    original.set.add(new Lazy(id, original.log, keys[r], optional, false));
                    indexed.set.add(new Lazy(id, indexed.log, keys[r], optional, false));
                } else if (kind < 999) {
                    original.set.add(new Keyed(id, original.log, keys[r]));
                    indexed.set.add(new Keyed(id, indexed.log, keys[r]));
                } else {
                    original.set.add(new Opaque(id, original.log, keys[r], false));
                    indexed.set.add(new Opaque(id, indexed.log, keys[r], false));
                }
            }
        }
        // An initial registry-creation pass plus 80 events for the registries, then a full pass.
        int events = 0;
        long originalNanos = 0;
        long indexedNanos = 0;
        for (int round = 0; round < 3; round++) {
            for (int r = 0; r < keys.length; r++) {
                long t0 = System.nanoTime();
                List<String> expected = original.event(keys[r]);
                long t1 = System.nanoTime();
                List<String> actual = indexed.event(keys[r]);
                long t2 = System.nanoTime();
                originalNanos += t1 - t0;
                indexedNanos += t2 - t1;
                assertEquals(expected, actual);
                events++;
            }
        }
        System.out.println("realistic set timing (cheap fake handlers, JIT cold): original "
                + originalNanos / 1_000_000 + " ms, indexed " + indexedNanos / 1_000_000 + " ms");
        assertEquals(original.log, indexed.log);
        HolderDispatch.Summary summary = indexed.engine.summary();
        assertFalse(summary.disabled());
        assertEquals(events, summary.events());
        // The first event visits all handlers once; later events only the matching registry plus opaque ones.
        assertTrue(summary.visited() * 8 < summary.fullPassEquivalent(),
                "visited " + summary.visited() + " of " + summary.fullPassEquivalent());
        System.out.println("realistic set: " + summary);
    }

    @Test
    void anUnnoticedSizeChangeStillTriggersARebuild() {
        Side original = new Side(false, 8);
        Side indexed = new Side(true, 8);
        for (int i = 0; i < 10; i++) {
            original.set.add(new Keyed(i, original.log, "a"));
            indexed.set.add(new Keyed(i, indexed.log, "a"));
        }
        assertEquals(original.event("a"), indexed.event("a"));
        // Changed behind the dispatcher's back (no mutated() call).
        original.set.add(new Keyed(99, original.log, "a"));
        indexed.set.add(new Keyed(99, indexed.log, "a"));
        original.event("a");
        indexed.event("a");
        assertEquals(original.log, indexed.log);
    }
}
