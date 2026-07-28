package dev.hoyin1600p.vhaccelerator;

import java.util.concurrent.atomic.AtomicLong;

public final class ServerLaunchTimer {
    private static final AtomicLong START_NANOS = new AtomicLong(-1L);
    private static final AtomicLong END_NANOS = new AtomicLong(-1L);

    private ServerLaunchTimer() {
    }

    public static void markStart() {
        if (START_NANOS.compareAndSet(-1L, System.nanoTime())) {
            if (VHAcceleratorConfig.instrumentationEnabled()) {
                VHAccelerator.LOGGER.info(
                        "Dedicated-server launch timer started"
                );
            }
        }
    }

    public static void markEnd() {
        long start = START_NANOS.get();
        if (start < 0L || !END_NANOS.compareAndSet(-1L, System.nanoTime())) {
            return;
        }

        long elapsedMillis = (END_NANOS.get() - start) / 1_000_000L;
        if (VHAcceleratorConfig.instrumentationEnabled()) {
            VHAccelerator.LOGGER.info(
                    "Dedicated server reached ServerStartedEvent in "
                            + "{} ms ({})",
                    elapsedMillis,
                    String.format(
                            "%.2f seconds",
                            elapsedMillis / 1000.0
                    )
            );
        }
    }
}
