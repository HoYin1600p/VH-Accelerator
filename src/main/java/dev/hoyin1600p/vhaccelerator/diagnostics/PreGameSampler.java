package dev.hoyin1600p.vhaccelerator.diagnostics;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Debug-only sampler for the launch stretch before Forge client mod loading:
 * Mixin configuration, Minecraft bootstrap and the Minecraft constructor.
 *
 * <p>It is started from VHA's mixin plugin, the earliest point VHA code runs,
 * and samples the launching thread (named {@code main}, later
 * {@code Render thread}) and Forge's background bootstrap thread (an
 * unnamed {@code pool-} thread the launch thread waits on) until the client
 * launch sampler takes over. The
 * mixin plugin may be loaded by a different class loader than game code, so
 * the hand-over uses a system property rather than shared static state.
 * Mod discovery happens before any mod code runs and cannot be sampled
 * here.</p>
 */
public final class PreGameSampler {
    private static final String STOP_PROPERTY = "vhaccelerator.pregame.sampler.stop";
    private static final long INTERVAL_MILLIS = 10L;
    private static final long MAX_RUNTIME_NANOS = 120_000_000_000L;
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");
    private static boolean started;

    private PreGameSampler() {
    }

    /** Starts sampling the calling (launch) thread; called once, debug only. */
    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        System.clearProperty(STOP_PROPERTY);
        Thread launchThread = Thread.currentThread();
        Thread sampler = new Thread(
                () -> run(launchThread),
                "VH Accelerator pre-game sampler"
        );
        sampler.setDaemon(true);
        sampler.setPriority(Thread.MAX_PRIORITY);
        sampler.start();
    }

    /** Asks a running pre-game sampler, in any class loader, to report. */
    public static void requestStop() {
        System.setProperty(STOP_PROPERTY, "true");
    }

    private static void run(Thread launchThread) {
        SampleBuckets buckets = new SampleBuckets();
        long started = System.nanoTime();
        long previous = started;
        Thread[] poolThreads = new Thread[0];
        int iteration = 0;
        ClassLoadingMXBean classes =
                ManagementFactory.getClassLoadingMXBean();
        long loadedBefore = classes.getTotalLoadedClassCount();
        while (System.getProperty(STOP_PROPERTY) == null
                && launchThread.isAlive()
                && System.nanoTime() - started < MAX_RUNTIME_NANOS) {
            try {
                Thread.sleep(INTERVAL_MILLIS);
            } catch (InterruptedException interrupted) {
                return;
            }
            if (iteration++ % 20 == 0) {
                poolThreads = poolThreads();
            }
            long now = System.nanoTime();
            buckets.add(
                    "launch-thread:" + launchThread.getName(),
                    launchThread.getStackTrace(),
                    now - previous
            );
            long loadedNow = classes.getTotalLoadedClassCount();
            buckets.addClassesLoaded("pre-game:" + launchThread.getName(), loadedNow - loadedBefore);
            loadedBefore = loadedNow;
            for (Thread pool : poolThreads) {
                if (pool.isAlive()) {
                    buckets.add("background-pool-threads", pool.getStackTrace(), now - previous);
                }
            }
            previous = now;
        }
        buckets.report("Pre-game sampler", LOGGER::info);
    }

    /** Unnamed executor threads, such as Forge's background bootstrap. */
    private static Thread[] poolThreads() {
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) {
            root = root.getParent();
        }
        Thread[] threads = new Thread[Math.max(16, root.activeCount() * 2)];
        int count = root.enumerate(threads, true);
        return Arrays.stream(threads, 0, count)
                .filter(thread -> thread.getName().startsWith("pool-"))
                .toArray(Thread[]::new);
    }
}
