package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.Util;

/**
 * Holds model-preparation worker state outside ModelBakery's transformed
 * class, avoiding any dependency on merged static-initializer ordering.
 */
public final class ModelPreparationCoordinator {
    private static final Executor EXECUTOR =
            Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "VH Accelerator model preparation coordinator"
                );
                thread.setDaemon(true);
                return thread;
            });
    private static final AtomicBoolean CAPACITY_WARNING = new AtomicBoolean();

    private ModelPreparationCoordinator() {
    }

    public static Executor executor() {
        return EXECUTOR;
    }

    public static boolean hasBackgroundCapacity() {
        Executor executor = Util.backgroundExecutor();
        int parallelism;
        if (executor instanceof ForkJoinPool pool) {
            parallelism = pool.getParallelism();
        } else if (executor instanceof ThreadPoolExecutor pool) {
            parallelism = pool.getMaximumPoolSize();
        } else {
            parallelism = Math.max(
                    1,
                    Runtime.getRuntime().availableProcessors() - 1
            );
        }
        if (parallelism >= 2) {
            return true;
        }
        if (CAPACITY_WARNING.compareAndSet(false, true)) {
            VHAccelerator.LOGGER.info(
                    "Using sequential model preparation because "
                            + "Minecraft exposes only {} background worker",
                    parallelism
            );
        }
        return false;
    }
}
