package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import java.util.Locale;

/**
 * Debug-only attribution for Forge's model-bake phases.
 *
 * <p>The original calls remain synchronous and retain their ordering. This
 * profiler only separates mod event dispatch from Forge's post-bake pass.
 */
public final class ModelBakeEventProfiler {
    private static boolean active;
    private static long eventStartedNanos;
    private static long eventDispatchNanos;
    private static long postBakeNanos;

    private ModelBakeEventProfiler() {
    }

    public static synchronized void begin() {
        active = VHAcceleratorConfig.debugDiagnosticsEnabled();
        eventStartedNanos = active ? System.nanoTime() : -1L;
        eventDispatchNanos = 0L;
        postBakeNanos = 0L;
    }

    public static synchronized boolean isActive() {
        return active;
    }

    public static synchronized void recordEventDispatch(long startedNanos) {
        if (!active || startedNanos < 0L) {
            return;
        }
        eventDispatchNanos += Math.max(
                0L,
                System.nanoTime() - startedNanos
        );
    }

    public static synchronized void recordPostBake(long startedNanos) {
        if (!active || startedNanos < 0L) {
            return;
        }
        postBakeNanos += Math.max(
                0L,
                System.nanoTime() - startedNanos
        );
    }

    public static synchronized void finish() {
        if (!active || eventStartedNanos < 0L) {
            active = false;
            return;
        }

        long totalNanos = Math.max(
                0L,
                System.nanoTime() - eventStartedNanos
        );
        long residualNanos = Math.max(
                0L,
                totalNanos - eventDispatchNanos - postBakeNanos
        );

        VHAccelerator.LOGGER.info(
                "Forge model-bake attribution: {} ms total, "
                        + "{} ms in mod event dispatch, "
                        + "{} ms in Forge post-bake, {} ms other",
                formatMillis(totalNanos),
                formatMillis(eventDispatchNanos),
                formatMillis(postBakeNanos),
                formatMillis(residualNanos)
        );

        active = false;
        eventStartedNanos = -1L;
        eventDispatchNanos = 0L;
        postBakeNanos = 0L;
    }

    private static String formatMillis(long nanos) {
        return String.format(
                Locale.ROOT,
                "%.3f",
                nanos / 1_000_000.0
        );
    }
}
