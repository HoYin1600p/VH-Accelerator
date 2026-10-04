package dev.hoyin1600p.vhaccelerator.client.profiling;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.diagnostics.PreGameSampler;
import dev.hoyin1600p.vhaccelerator.diagnostics.SampleBuckets;
import dev.hoyin1600p.vhaccelerator.diagnostics.StackAttribution;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Debug-only launch sampler that attributes model-loading time to mods.
 *
 * <p>Phase timers show how long ModelBakery preparation takes, but not whose
 * code runs inside Forge events such as {@code TextureStitchEvent.Pre}. While
 * the initial model load runs, this samples the stacks of just two threads,
 * the model-preparation thread and the render thread, about every
 * {@link #INTERVAL_MILLIS} ms. Each sample is attributed to the first frame
 * outside the JDK, Minecraft, Forge and common libraries, i.e. to the mod
 * doing the work, and reported per phase at launch completion.</p>
 *
 * <p>It is started only when launch profiling is enabled, holds bounded
 * string counters, and is discarded after its single report.</p>
 */
public final class LaunchStackSampler {
    static final long INTERVAL_MILLIS = 10L;
    private static final Object LOCK = new Object();
    private static Thread sampler;
    private static volatile Thread preparationThread;
    private static volatile Thread renderThread;
    private static volatile String preparationPhase = "startup";
    private static volatile String renderPhase = "resource-reload";
    private static volatile List<Thread> workerThreads = List.of();
    private static volatile boolean running;
    private static boolean reported;
    private static final SampleBuckets BUCKETS = new SampleBuckets();

    private LaunchStackSampler() {
    }

    /**
     * Starts sampling at the beginning of Forge client mod loading, from the
     * render thread: that thread (registry events) and Forge's parallel
     * {@code modloading-worker} threads (mod construction and setup).
     */
    public static void startLaunch() {
        synchronized (LOCK) {
            if (reported || running) {
                return;
            }
            PreGameSampler.requestStop();
            renderThread = Thread.currentThread();
            renderPhase = "mod-construction-and-registries";
            startSampler();
        }
    }

    /** Sets the label for render-thread and mod-loading-worker samples. */
    public static void renderPhase(String name) {
        if (running) {
            renderPhase = name;
        }
    }

    /** Starts (or joins) sampling of the calling model-preparation thread. */
    public static void start(String initialPhase) {
        synchronized (LOCK) {
            if (reported) {
                return;
            }
            preparationThread = Thread.currentThread();
            preparationPhase = initialPhase;
            renderPhase = "resource-reload";
            if (running) {
                return;
            }
            renderThread = findThread("Render thread");
            startSampler();
        }
    }

    private static void startSampler() {
        running = true;
        sampler = new Thread(LaunchStackSampler::run, "VH Accelerator launch sampler");
        sampler.setDaemon(true);
        sampler.setPriority(Thread.MAX_PRIORITY);
        sampler.start();
    }

    public static void phase(String name) {
        if (running) {
            preparationPhase = name;
        }
    }

    /** Called when the initial model preparation returns. */
    public static void preparationFinished() {
        if (running) {
            preparationPhase = "after-preparation";
        }
    }

    /** Stops sampling and logs the attribution once. */
    public static void stopAndReport() {
        Thread current;
        synchronized (LOCK) {
            if (!running) {
                return;
            }
            running = false;
            current = sampler;
            sampler = null;
        }
        try {
            current.join(TimeUnit.SECONDS.toMillis(1));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        synchronized (LOCK) {
            reported = true;
            report();
            BUCKETS.clear();
            preparationThread = null;
            renderThread = null;
            workerThreads = List.of();
        }
    }

    private static void run() {
        long previous = System.nanoTime();
        int iteration = 0;
        ClassLoadingMXBean classes =
                ManagementFactory.getClassLoadingMXBean();
        long loadedBefore = classes.getTotalLoadedClassCount();
        while (running) {
            try {
                Thread.sleep(INTERVAL_MILLIS);
            } catch (InterruptedException interrupted) {
                return;
            }
            if (iteration++ % 50 == 0) {
                workerThreads = findThreadsStartingWith("modloading-worker");
            }
            long now = System.nanoTime();
            long elapsed = now - previous;
            previous = now;
            String phase = renderPhase;
            long loadedNow = classes.getTotalLoadedClassCount();
            synchronized (LOCK) {
                BUCKETS.addClassesLoaded(phase, loadedNow - loadedBefore);
            }
            loadedBefore = loadedNow;
            if (preparationThread != null) {
                sample(preparationThread, "model-preparation:" + preparationPhase, elapsed);
            }
            sample(renderThread, "render-thread:" + phase, elapsed);
            for (Thread worker : workerThreads) {
                // Summed across workers: parallel CPU time, not wall time.
                sample(worker, "modloading-workers:" + phase, elapsed);
            }
        }
    }

    private static void sample(Thread thread, String bucketName, long elapsedNanos) {
        if (thread == null || !thread.isAlive()) {
            return;
        }
        StackTraceElement[] stack = thread.getStackTrace();
        synchronized (LOCK) {
            BUCKETS.add(bucketName, stack, elapsedNanos);
        }
    }

    static String owner(StackTraceElement[] stack) {
        return StackAttribution.owner(stack);
    }

    static String packagePrefix(String className) {
        return StackAttribution.packagePrefix(className);
    }

    private static List<Thread> findThreadsStartingWith(String prefix) {
        List<Thread> threads = new ArrayList<>();
        for (Thread thread : liveThreads()) {
            if (thread.getName().startsWith(prefix)) {
                threads.add(thread);
            }
        }
        return List.copyOf(threads);
    }

    private static Thread findThread(String name) {
        for (Thread thread : liveThreads()) {
            if (name.equals(thread.getName())) {
                return thread;
            }
        }
        return null;
    }

    /** Enumerates live threads without capturing their stacks. */
    private static Thread[] liveThreads() {
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) {
            root = root.getParent();
        }
        Thread[] threads = new Thread[Math.max(16, root.activeCount() * 2)];
        int count = root.enumerate(threads, true);
        return Arrays.copyOf(threads, count);
    }

    private static void report() {
        if (!BUCKETS.isEmpty()) {
            BUCKETS.report("Launch sampler", VHAccelerator.LOGGER::info);
        }
    }
}
