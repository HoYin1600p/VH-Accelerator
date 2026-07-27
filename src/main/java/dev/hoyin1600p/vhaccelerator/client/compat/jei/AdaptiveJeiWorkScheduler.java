package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.function.Supplier;

/**
 * Keeps explicitly safe JEI collection work off Minecraft's shared common
 * pool. Loading screens get a bounded burst of parallelism; after the first
 * playable frame, new work uses a smaller low-priority pool.
 *
 * @author hoyin1600p
 */
public final class AdaptiveJeiWorkScheduler {
    private static final ClassLoader GAME_CLASS_LOADER =
            AdaptiveJeiWorkScheduler.class.getClassLoader();
    private static final int AVAILABLE_PROCESSORS =
            Math.max(1, Runtime.getRuntime().availableProcessors());
    private static final int LOADING_WORKERS =
            Math.max(1, Math.min(12, AVAILABLE_PROCESSORS / 2));
    private static final int GAMEPLAY_WORKERS =
            Math.max(1, Math.min(4, AVAILABLE_PROCESSORS / 4));

    private static final ForkJoinPool LOADING_POOL =
            createPool("Loading", LOADING_WORKERS);
    private static final ForkJoinPool GAMEPLAY_POOL =
            createPool("Gameplay", GAMEPLAY_WORKERS);

    private static volatile boolean gameplayActive;

    private AdaptiveJeiWorkScheduler() {
    }

    public static void initialize() {
        VHAccelerator.LOGGER.info(
                "Adaptive JEI scheduler detected {} logical processors "
                        + "[loading workers={}, gameplay workers={}, classloader={}]",
                AVAILABLE_PROCESSORS,
                LOADING_WORKERS,
                GAMEPLAY_WORKERS,
                GAME_CLASS_LOADER.getClass().getName()
        );
    }

    public static void markLoading() {
        gameplayActive = false;
    }

    public static void markGameplayActive() {
        gameplayActive = true;
    }

    public static int currentParallelism() {
        return gameplayActive ? GAMEPLAY_WORKERS : LOADING_WORKERS;
    }

    public static <T> T invokeParallel(Supplier<T> task) {
        ForkJoinPool pool = gameplayActive ? GAMEPLAY_POOL : LOADING_POOL;
        if (pool.getParallelism() <= 1) {
            return runWithGameClassLoader(task);
        }
        if (ForkJoinTaskContext.isRunningIn(pool)) {
            return runWithGameClassLoader(task);
        }
        return pool.submit(() -> runWithGameClassLoader(task)).join();
    }

    /**
     * Runs one stateful build on a dedicated low-priority JEI pool. The task
     * itself must remain single-threaded and must not mutate a live JEI object.
     */
    public static <T> CompletableFuture<T> submitIsolated(Supplier<T> task) {
        ForkJoinPool pool = gameplayActive ? GAMEPLAY_POOL : LOADING_POOL;
        return CompletableFuture.supplyAsync(
                () -> runWithGameClassLoader(task),
                pool
        );
    }

    private static <T> T runWithGameClassLoader(Supplier<T> task) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        if (previous != GAME_CLASS_LOADER) {
            thread.setContextClassLoader(GAME_CLASS_LOADER);
        }
        try {
            verifyForgeVisibility();
            return task.get();
        } finally {
            if (previous != GAME_CLASS_LOADER) {
                thread.setContextClassLoader(previous);
            }
        }
    }

    private static void verifyForgeVisibility() {
        try {
            Class.forName(
                    "net.minecraftforge.event.ItemAttributeModifierEvent",
                    false,
                    Thread.currentThread().getContextClassLoader()
            );
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "Adaptive worker cannot access Forge's transformed classes",
                    exception
            );
        }
    }

    private static ForkJoinPool createPool(String phase, int workers) {
        return new ForkJoinPool(
                workers,
                pool -> {
                    ForkJoinWorkerThread thread =
                            ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                    thread.setName(
                            "VH-Accelerator-JEI-" + phase + "-" + thread.getPoolIndex()
                    );
                    thread.setContextClassLoader(GAME_CLASS_LOADER);
                    thread.setDaemon(true);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                },
                (thread, throwable) -> VHAccelerator.LOGGER.error(
                        "Uncaught adaptive JEI worker failure on {}",
                        thread.getName(),
                        throwable
                ),
                true
        );
    }

    /**
     * Isolated to keep the public scheduler API independent from ForkJoinTask.
     */
    private static final class ForkJoinTaskContext {
        private ForkJoinTaskContext() {
        }

        private static boolean isRunningIn(ForkJoinPool pool) {
            return ForkJoinPool.commonPool() != pool
                    && java.util.concurrent.ForkJoinTask.inForkJoinPool()
                    && java.util.concurrent.ForkJoinTask.getPool() == pool;
        }
    }
}
