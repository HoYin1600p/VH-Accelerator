package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds model-preparation worker state outside ModelBakery's transformed
 * class, avoiding any dependency on merged static-initializer ordering.
 */
public final class ModelPreparationCoordinator {
    private static final Executor EXECUTOR = SharedWorkers.compute();
    private static final AtomicBoolean CAPACITY_WARNING = new AtomicBoolean();

    private ModelPreparationCoordinator() {
    }

    public static Executor executor() {
        return EXECUTOR;
    }

    public static boolean hasBackgroundCapacity() {
        int parallelism = SharedWorkers.budget().compute();
        if (parallelism >= 2) { return true; }
        if (CAPACITY_WARNING.compareAndSet(false, true)) {
            VHAccelerator.LOGGER.info("Using sequential model preparation within the shared worker budget [{} compute workers]", parallelism);
        }
        return false;
    }
}
