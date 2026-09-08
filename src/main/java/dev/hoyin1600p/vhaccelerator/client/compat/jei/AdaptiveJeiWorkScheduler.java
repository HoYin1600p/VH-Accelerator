package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Keeps explicitly safe JEI collection work off Minecraft's shared common
 * pool. All phases share VHA's hardware-sized worker budget.
 *
 * @author hoyin1600p
 */
public final class AdaptiveJeiWorkScheduler {
    private static final ClassLoader GAME_CLASS_LOADER =
            AdaptiveJeiWorkScheduler.class.getClassLoader();
    private static final int AVAILABLE_PROCESSORS =
            Math.max(1, Runtime.getRuntime().availableProcessors());
    private static volatile boolean gameplayActive;

    private AdaptiveJeiWorkScheduler() {
    }

    public static void initialize() {
        VHAccelerator.LOGGER.info(
                "Shared VHA scheduler detected {} logical processors [compute={}, I/O={}, total budget={}]",
                AVAILABLE_PROCESSORS, SharedWorkers.budget().compute(),
                SharedWorkers.budget().io(), SharedWorkers.budget().total());
    }

    public static void markLoading() {
        gameplayActive = false;
    }

    public static void markGameplayActive() {
        gameplayActive = true;
    }

    public static int currentParallelism() {
        return gameplayActive ? 1 : Math.max(1, SharedWorkers.budget().compute());
    }

    public static <T> T invokeParallel(Supplier<T> task) {
        return SharedWorkers.invoke(() -> runWithGameClassLoader(task));
    }

    /**
     * Runs one stateful build on a shared low-priority compute pool. The task
     * itself must remain single-threaded and must not mutate a live JEI object.
     */
    public static <T> CompletableFuture<T> submitIsolated(Supplier<T> task) {
        return CompletableFuture.supplyAsync(
                () -> runWithGameClassLoader(task), SharedWorkers.compute());
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

    public static <T> java.util.stream.Stream<T> stream(java.util.Collection<T> values) {
        return gameplayActive ? values.stream() : SharedWorkers.stream(values);
    }
}
