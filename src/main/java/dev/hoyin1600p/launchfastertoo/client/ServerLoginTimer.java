package dev.hoyin1600p.launchfastertoo.client;

import dev.hoyin1600p.launchfastertoo.LaunchFasterToo;

/**
 * Measures a multiplayer connection from the opening of vanilla's connect
 * screen to the first playable world frame after the client player exists.
 */
public final class ServerLoginTimer {
    private static long attempt;
    private static long startNanos = -1L;
    private static long playerReadyNanos = -1L;
    private static Sample lastSample;

    private ServerLoginTimer() {
    }

    public static synchronized void markStart() {
        attempt++;
        startNanos = System.nanoTime();
        playerReadyNanos = -1L;

        LaunchFasterToo.LOGGER.info(
                "Server login timer started for attempt {} "
                        + "[asyncJeiStartup={}, stagedVaultGroupLoading={}, "
                        + "parallelJeiIngredientSorting={}, parallelJeiTweakerMatching={}]",
                attempt,
                LaunchFasterTooClientConfig.VALUES.asyncJeiStartup.get(),
                LaunchFasterTooClientConfig.VALUES.stagedVaultGroupLoading.get(),
                LaunchFasterTooClientConfig.VALUES.parallelJeiIngredientSorting.get(),
                LaunchFasterTooClientConfig.VALUES.parallelJeiTweakerMatching.get()
        );
    }

    public static synchronized boolean markPlayerReady() {
        if (startNanos < 0L || playerReadyNanos >= 0L) {
            return false;
        }

        playerReadyNanos = System.nanoTime();
        LaunchFasterToo.LOGGER.info(
                "Server login attempt {} initialized the client player after {} ms",
                attempt,
                nanosToMillis(playerReadyNanos - startNanos)
        );
        return true;
    }

    public static synchronized Sample markFirstPlayableFrame() {
        if (startNanos < 0L || playerReadyNanos < 0L) {
            return null;
        }

        long frameNanos = System.nanoTime();
        Sample sample = new Sample(
                attempt,
                nanosToMillis(frameNanos - startNanos),
                nanosToMillis(playerReadyNanos - startNanos),
                nanosToMillis(frameNanos - playerReadyNanos)
        );
        lastSample = sample;
        startNanos = -1L;
        playerReadyNanos = -1L;

        LaunchFasterToo.LOGGER.info(
                "Server login completed in {} ms ({}) "
                        + "[client player: {} ms, first playable frame: {} ms]",
                sample.totalMillis(),
                String.format("%.2f seconds", sample.totalMillis() / 1000.0),
                sample.playerReadyMillis(),
                sample.firstFrameMillis()
        );
        return sample;
    }

    public static synchronized void cancelActiveAttempt() {
        if (startNanos < 0L) {
            return;
        }

        LaunchFasterToo.LOGGER.info(
                "Server login attempt {} ended before its first playable frame",
                attempt
        );
        startNanos = -1L;
        playerReadyNanos = -1L;
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
        private final long playerReadyMillis;
        private final long firstFrameMillis;

        private Sample(
                long attempt,
                long totalMillis,
                long playerReadyMillis,
                long firstFrameMillis
        ) {
            this.attempt = attempt;
            this.totalMillis = totalMillis;
            this.playerReadyMillis = playerReadyMillis;
            this.firstFrameMillis = firstFrameMillis;
        }

        public long attempt() {
            return attempt;
        }

        public long totalMillis() {
            return totalMillis;
        }

        public long playerReadyMillis() {
            return playerReadyMillis;
        }

        public long firstFrameMillis() {
            return firstFrameMillis;
        }
    }
}
