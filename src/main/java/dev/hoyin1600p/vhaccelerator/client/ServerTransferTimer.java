package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;

/**
 * Measures an established connection's world replacement from the first
 * transfer signal to the first playable frame. Proxy backend switches use
 * Minecraft's respawn packet, which is also used for dimension changes.
 */
public final class ServerTransferTimer {
    private static long attempt;
    private static long startNanos = -1L;
    private static Sample lastSample;

    private ServerTransferTimer() {
    }

    public static synchronized boolean markStart(String trigger) {
        if (startNanos >= 0L) {
            return false;
        }

        attempt++;
        startNanos = System.nanoTime();
        if (VHAcceleratorConfig.instrumentationEnabled()) {
            VHAccelerator.LOGGER.info(
                "Server/world transfer timer started for attempt {} from {} "
                        + "[asyncJeiSearchIndex={}, parallelVanillaRecipeValidation={}, "
                        + "optimizeJeiIngredientFilterConstruction={}, "
                        + "stagedVaultGroupLoading={}, parallelJeiIngredientSorting={}, "
                        + "parallelJeiTweakerMatching={}]",
                attempt,
                trigger,
                VHAcceleratorClientConfig.VALUES.asyncJeiSearchIndex.get(),
                VHAcceleratorClientConfig.VALUES.parallelVanillaRecipeValidation.get(),
                VHAcceleratorClientConfig.VALUES
                        .optimizeJeiIngredientFilterConstruction
                        .get(),
                VHAcceleratorClientConfig.VALUES.stagedVaultGroupLoading.get(),
                VHAcceleratorClientConfig.VALUES.parallelJeiIngredientSorting.get(),
                    VHAcceleratorClientConfig.VALUES
                            .parallelJeiTweakerMatching
                            .get()
            );
        }
        return true;
    }

    public static synchronized Sample markFirstPlayableFrame() {
        if (startNanos < 0L) {
            return null;
        }

        long frameNanos = System.nanoTime();
        Sample sample = new Sample(attempt, nanosToMillis(frameNanos - startNanos));
        lastSample = sample;
        startNanos = -1L;

        if (VHAcceleratorConfig.instrumentationEnabled()) {
            VHAccelerator.LOGGER.info(
                    "Server/world transfer completed in {} ms ({})",
                    sample.totalMillis(),
                    String.format(
                            "%.2f seconds",
                            sample.totalMillis() / 1000.0
                    )
            );
        }
        return sample;
    }

    public static synchronized void cancelActiveAttempt() {
        if (startNanos < 0L) {
            return;
        }

        if (VHAcceleratorConfig.instrumentationEnabled()) {
            VHAccelerator.LOGGER.info(
                    "Server/world transfer attempt {} ended before its "
                            + "first playable frame",
                    attempt
            );
        }
        startNanos = -1L;
    }

    public static synchronized Sample lastSample() {
        return lastSample;
    }

    public static synchronized boolean isActive() {
        return startNanos >= 0L;
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    public static final class Sample {
        private final long attempt;
        private final long totalMillis;

        private Sample(long attempt, long totalMillis) {
            this.attempt = attempt;
            this.totalMillis = totalMillis;
        }

        public long attempt() {
            return attempt;
        }

        public long totalMillis() {
            return totalMillis;
        }
    }
}
