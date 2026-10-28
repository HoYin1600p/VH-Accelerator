package dev.hoyin1600p.vhaccelerator.resource;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.resource.ImmutablePathPackIndex;

import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

/**
 * Builds the {@link ImmutablePathPackIndex} of every known mod pack on a few
 * background lanes as soon as the first pack is queried.
 *
 * <p>Each pack's index is independent of every other, so a pack built on a
 * worker is identical to one built by the thread that queried it; no results
 * are merged. Each pack still builds under its own lock, which is the only
 * thing any thread waits for: a querying thread that finds its pack mid-build
 * waits for that one build, and a pack whose task has not started yet is
 * simply built by the querying thread (the task then finds it built). Nothing
 * joins a queued task, so a full or stalled pool can never block a reload.</p>
 */
public final class PackIndexPrewarmer {
    /** A pack that can produce its index off the querying thread. */
    public interface Source {
        /** The pack's index, created if needed, or null when the pack is not indexable. */
        @Nullable
        ImmutablePathPackIndex vhaccelerator$prewarmIndex();
    }

    /** Beyond this many registered packs, dead references are purged on registration. */
    private static final int PURGE_THRESHOLD = 4096;
    private static final ConcurrentLinkedQueue<WeakReference<Source>> PENDING =
            new ConcurrentLinkedQueue<>();

    private static final int DEFAULT_LANES = 8;
    private static final AtomicInteger REGISTERED = new AtomicInteger();

    private PackIndexPrewarmer() {
    }

    /** Records a freshly constructed pack. Does no I/O and calls nothing on the pack. */
    public static void register(Source pack) {
        PENDING.add(new WeakReference<>(pack));
        if (REGISTERED.incrementAndGet() > PURGE_THRESHOLD) {
            PENDING.removeIf(reference -> reference.get() == null);
            REGISTERED.set(PENDING.size());
        }
    }

    /**
     * Called when {@code current} is queried for the first time. Hands every
     * other registered pack to the worker pool and returns immediately.
     */
    public static void kick(@Nullable Source current) {
        if (PENDING.isEmpty()) {
            return;
        }
        int lanes = lanes();
        if (lanes < 1 || SharedWorkers.budget().compute() < 2) {
            PENDING.clear();
            return;
        }
        List<Source> packs = drain(PENDING, current);
        REGISTERED.set(0);
        if (packs.isEmpty()) {
            return;
        }
        // A few lanes pull packs from one queue: bounded concurrency, and the
        // shared pool is never filled with one task per pack.
        ConcurrentLinkedQueue<Source> work = new ConcurrentLinkedQueue<>(packs);
        Runnable lane = () -> {
            Source pack;
            while ((pack = work.poll()) != null) {
                prewarm(pack);
            }
        };
        Executor executor = executor(lanes);
        for (int count = 0, started = Math.min(lanes, packs.size()); count < started; count++) {
            executor.execute(lane);
        }
    }

    private static ThreadPoolExecutor pool;

    /** At most {@code lanes} daemon threads that exit after 10 idle seconds; never the shared pool. */
    private static synchronized Executor executor(int lanes) {
        if (pool == null) {
            ClassLoader loader = PackIndexPrewarmer.class.getClassLoader();
            AtomicInteger number = new AtomicInteger();
            ThreadPoolExecutor created = new ThreadPoolExecutor(lanes, lanes, 10L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(), task -> {
                        Thread thread = new Thread(task, "VH Accelerator pack index-" + number.incrementAndGet());
                        thread.setDaemon(true);
                        thread.setContextClassLoader(loader);
                        return thread;
                    });
            created.allowCoreThreadTimeOut(true);
            pool = created;
        }
        return pool;
    }

    static int lanes() {
        int configured = Integer.getInteger("vhaccelerator.packIndexLanes", DEFAULT_LANES);
        return Math.max(0, Math.min(configured, SharedWorkers.budget().compute()));
    }

    static List<Source> drain(ConcurrentLinkedQueue<WeakReference<Source>> queue, @Nullable Source skip) {
        List<Source> packs = new ArrayList<>();
        WeakReference<Source> reference;
        while ((reference = queue.poll()) != null) {
            Source pack = reference.get();
            if (pack != null && pack != skip) {
                packs.add(pack);
            }
        }
        return packs;
    }

    static void prewarm(Source pack) {
        try {
            ImmutablePathPackIndex index = pack.vhaccelerator$prewarmIndex();
            if (index != null) {
                index.prewarm();
            }
        } catch (Throwable ignored) {
            // A pack left unbuilt is built lazily by its first query, as before.
        }
    }

    static int pendingForTesting() {
        return PENDING.size();
    }
}
