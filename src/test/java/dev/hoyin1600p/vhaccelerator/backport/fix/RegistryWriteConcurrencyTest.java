package dev.hoyin1600p.vhaccelerator.backport.fix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Drives a real vanilla {@code MappedRegistry} from 32 threads released together by a barrier, the way
 * Forge 40's parallel mod construction does, and checks that nothing is lost, duplicated or thrown.
 *
 * <p>The unit tests run without Mixin, so the raw calls are vanilla's unsynchronised ones (the
 * "red" arm, tagged {@code stress} and excluded from the default build) and the locked calls wrap
 * each one exactly as {@code MappedRegistryWriteLockMixin} does, with the registry as the monitor
 * (the "green" arm, which runs by default). {@code getOrCreateTag} is VHA's separate tag backport,
 * which takes the same monitor, so the green arm wraps it the same way.
 */
class RegistryWriteConcurrencyTest {
    private static final int THREADS = 32;
    private static final int KEYS_PER_THREAD = 200;
    private static final int ROUNDS = 200;
    private static final int TAGS = 8;
    private static final long ROUND_TIMEOUT_MILLIS = 20_000;

    @BeforeAll
    static void fakeBootstrap() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Field guard = Bootstrap.class.getDeclaredField("isBootstrapped");
        guard.setAccessible(true);
        guard.setBoolean(null, true);
    }

    /** The registry calls a mod can make from its constructor. */
    interface Calls {
        void register(MappedRegistry<Object> registry, ResourceKey<Object> key, Object value);

        Holder<Object> holder(MappedRegistry<Object> registry, ResourceKey<Object> key);

        HolderSet.Named<Object> tag(MappedRegistry<Object> registry, TagKey<Object> key);
    }

    private static final Calls RAW = new Calls() {
        public void register(MappedRegistry<Object> registry, ResourceKey<Object> key, Object value) {
            registry.register(key, value, Lifecycle.stable());
        }

        public Holder<Object> holder(MappedRegistry<Object> registry, ResourceKey<Object> key) {
            return registry.getOrCreateHolder(key);
        }

        public HolderSet.Named<Object> tag(MappedRegistry<Object> registry, TagKey<Object> key) {
            return registry.getOrCreateTag(key);
        }
    };

    private static final Calls LOCKED = new Calls() {
        public void register(MappedRegistry<Object> registry, ResourceKey<Object> key, Object value) {
            synchronized (RegistryWriteLock.monitor(registry)) {
                registry.register(key, value, Lifecycle.stable());
            }
        }

        public Holder<Object> holder(MappedRegistry<Object> registry, ResourceKey<Object> key) {
            synchronized (RegistryWriteLock.monitor(registry)) {
                return registry.getOrCreateHolder(key);
            }
        }

        public HolderSet.Named<Object> tag(MappedRegistry<Object> registry, TagKey<Object> key) {
            synchronized (RegistryWriteLock.monitor(registry)) {
                return registry.getOrCreateTag(key);
            }
        }
    };

    /** Anomalies seen in one round, split so the tag check can be skipped for the vanilla arm. */
    record Round(List<String> registryAnomalies, List<String> tagAnomalies, boolean hung) {
        boolean clean(boolean includeTags) {
            return registryAnomalies.isEmpty() && (!includeTags || tagAnomalies.isEmpty());
        }
    }

    record Summary(int roundsRun, int roundsWithRegistryAnomalies, int roundsWithTagAnomalies, String first) {
    }


    private static Round runRound(Calls calls, int round) throws InterruptedException {
        ResourceKey<net.minecraft.core.Registry<Object>> registryKey =
                ResourceKey.createRegistryKey(new ResourceLocation("vhatest", "root_" + round));
        MappedRegistry<Object> registry = new MappedRegistry<>(registryKey, Lifecycle.stable(), null);
        Object[][] values = new Object[THREADS][KEYS_PER_THREAD];
        ResourceKey<Object>[][] keys = newKeyTable(registryKey);
        for (int t = 0; t < THREADS; t++) {
            for (int i = 0; i < KEYS_PER_THREAD; i++) {
                values[t][i] = new Object();
            }
        }
        TagKey<Object>[] tagKeys = newTagKeys(registryKey);
        HolderSet.Named<?>[][] tagsSeen = new HolderSet.Named<?>[THREADS][TAGS];
        ConcurrentLinkedQueue<String> thrown = new ConcurrentLinkedQueue<>();
        CyclicBarrier start = new CyclicBarrier(THREADS);
        Thread[] workers = new Thread[THREADS];
        for (int t = 0; t < THREADS; t++) {
            int me = t;
            workers[t] = new Thread(() -> {
                try {
                    start.await();
                    int neighbour = (me + 1) % THREADS;
                    for (int i = 0; i < KEYS_PER_THREAD; i++) {
                        // A holder for a key another thread registers a moment later (Forge allows it).
                        calls.holder(registry, keys[neighbour][i]);
                        if ((i & 1) == 1) {
                            calls.holder(registry, keys[me][i]);
                        }
                        calls.register(registry, keys[me][i], values[me][i]);
                        if (i < TAGS) {
                            tagsSeen[me][i] = calls.tag(registry, tagKeys[i]);
                        }
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    thrown.add("thread " + me + " interrupted at the barrier");
                } catch (Throwable e) {
                    thrown.add("thread " + me + ": " + e);
                }
            }, "registry-stress-" + t);
            workers[t].setDaemon(true);
        }
        for (Thread worker : workers) {
            worker.start();
        }
        long deadline = System.nanoTime() + ROUND_TIMEOUT_MILLIS * 1_000_000;
        boolean hung = false;
        for (Thread worker : workers) {
            worker.join(Math.max(1, (deadline - System.nanoTime()) / 1_000_000));
            if (worker.isAlive()) {
                hung = true;
                thrown.add(worker.getName() + " hung");
            }
        }
        if (hung) {
            // A corrupted HashMap can send a writer into an endless loop that burns a core until the
            // JVM exits; stop what can be stopped (deprecated, but present on the Java 17 toolchain).
            for (Thread worker : workers) {
                if (worker.isAlive()) {
                    stopQuietly(worker);
                }
            }
        }

        List<String> registryAnomalies = new ArrayList<>(thrown);
        if (!registryAnomalies.isEmpty()) {
            // A thread that threw or hung leaves the registry partial; the state checks add nothing.
            return new Round(registryAnomalies, tagAnomalies(tagsSeen), hung);
        }
        int expected = THREADS * KEYS_PER_THREAD;
        try {
            if (registry.size() != expected) {
                registryAnomalies.add("size " + registry.size() + " instead of " + expected);
            }
            BitSet ids = new BitSet(expected);
            for (int t = 0; t < THREADS && registryAnomalies.size() < 5; t++) {
                for (int i = 0; i < KEYS_PER_THREAD; i++) {
                    Object value = values[t][i];
                    int id = registry.getId(value);
                    if (id < 0 || id >= expected) {
                        registryAnomalies.add("value of t" + t + "_" + i + " has id " + id);
                        continue;
                    }
                    if (ids.get(id)) {
                        registryAnomalies.add("id " + id + " is shared");
                    }
                    ids.set(id);
                    if (registry.byId(id) != value) {
                        registryAnomalies.add("byId(" + id + ") is not the value of t" + t + "_" + i);
                    }
                    if (registry.get(keys[t][i]) != value) {
                        registryAnomalies.add("get(key) is not the value of t" + t + "_" + i);
                    }
                    if (registry.get(keys[t][i].location()) != value) {
                        registryAnomalies.add("get(location) is not the value of t" + t + "_" + i);
                    }
                    Holder<Object> holder = registry.getHolder(keys[t][i]).orElse(null);
                    if (holder == null || !holder.isBound() || holder.value() != value) {
                        registryAnomalies.add("holder of t" + t + "_" + i + " is missing or unbound");
                    }
                }
            }
            if (registryAnomalies.isEmpty() && ids.cardinality() != expected) {
                registryAnomalies.add("only " + ids.cardinality() + " distinct ids for " + expected + " values");
            }
        } catch (RuntimeException e) {
            registryAnomalies.add("verification failed: " + e);
        }
        return new Round(registryAnomalies, tagAnomalies(tagsSeen), false);
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static void stopQuietly(Thread worker) {
        try {
            worker.stop();
        } catch (RuntimeException | Error ignored) {
            // Best effort only.
        }
    }

    private static List<String> tagAnomalies(HolderSet.Named<?>[][] seen) {
        List<String> anomalies = new ArrayList<>();
        for (int k = 0; k < TAGS; k++) {
            HolderSet.Named<?> first = null;
            for (int t = 0; t < THREADS; t++) {
                HolderSet.Named<?> tag = seen[t][k];
                if (tag == null) {
                    continue;
                }
                if (first == null) {
                    first = tag;
                } else if (first != tag) {
                    anomalies.add("tag " + k + " has more than one instance");
                    break;
                }
            }
        }
        return anomalies;
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<Object>[][] newKeyTable(ResourceKey<? extends net.minecraft.core.Registry<Object>> registryKey) {
        ResourceKey<Object>[][] keys = (ResourceKey<Object>[][]) new ResourceKey<?>[THREADS][KEYS_PER_THREAD];
        for (int t = 0; t < THREADS; t++) {
            for (int i = 0; i < KEYS_PER_THREAD; i++) {
                keys[t][i] = ResourceKey.create(registryKey, new ResourceLocation("vhatest", "t" + t + "_" + i));
            }
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static TagKey<Object>[] newTagKeys(ResourceKey<? extends net.minecraft.core.Registry<Object>> registryKey) {
        TagKey<Object>[] tags = (TagKey<Object>[]) new TagKey<?>[TAGS];
        for (int k = 0; k < TAGS; k++) {
            tags[k] = TagKey.create(registryKey, new ResourceLocation("vhatest", "tag" + k));
        }
        return tags;
    }

    private static Summary run(Calls calls, int maxRounds, boolean stopAtFirstAnomaly, boolean includeTags)
            throws InterruptedException {
        int run = 0;
        int registryBad = 0;
        int tagBad = 0;
        String first = null;
        for (int round = 0; round < maxRounds; round++) {
            Round result = runRound(calls, round);
            run++;
            if (!result.registryAnomalies().isEmpty()) {
                registryBad++;
                if (first == null) {
                    first = "round " + round + ": " + result.registryAnomalies().get(0);
                }
            }
            if (!result.tagAnomalies().isEmpty()) {
                tagBad++;
                if (first == null && includeTags) {
                    first = "round " + round + ": " + result.tagAnomalies().get(0);
                }
            }
            if (result.hung() || (stopAtFirstAnomaly && !result.clean(includeTags))) {
                // After a hang the endless loops may still be running, so go no further.
                break;
            }
        }
        return new Summary(run, registryBad, tagBad, first);
    }

    @Test
    void lockedWritesAreConsistent() throws InterruptedException {
        Summary summary = run(LOCKED, ROUNDS, false, true);
        System.out.println("registry-stress locked: " + summary);
        assertEquals(ROUNDS, summary.roundsRun());
        assertEquals(0, summary.roundsWithRegistryAnomalies(), "registry anomalies: " + summary.first());
        assertEquals(0, summary.roundsWithTagAnomalies(), "tag anomalies: " + summary.first());
    }

    @Test
    @Tag("stress")
    void vanillaRegistryRacesUnderParallelConstruction() throws InterruptedException {
        Summary summary = run(RAW, ROUNDS, true, false);
        System.out.println("registry-stress vanilla (stops at first anomaly): " + summary);
        assertTrue(summary.roundsWithRegistryAnomalies() > 0,
                "no anomaly in " + summary.roundsRun() + " rounds; raise the round count, do not weaken the locked arm");
    }

    @Test
    @Tag("stress")
    void vanillaAnomalyRateOverAllRounds() throws InterruptedException {
        Summary summary = run(RAW, ROUNDS, false, false);
        System.out.println("registry-stress vanilla (all rounds): " + summary);
        assertTrue(summary.roundsWithRegistryAnomalies() > 0, "no anomaly in " + summary.roundsRun() + " rounds");
    }
}
