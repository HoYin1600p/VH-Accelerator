package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;

/**
 * Separates the synchronous network-channel close from Minecraft's client
 * teardown and the final screen transition.
 */
public final class DisconnectTimer {
    private static long startedNanos = -1L;
    private static long networkCloseNanos = -1L;
    private static long teardownStartedNanos = -1L;
    private static long teardownNanos = -1L;
    private static long clearLevelPhaseStartedNanos = -1L;
    private static long clearLevelLastMarkerNanos = -1L;
    private static Sample lastSample;

    private DisconnectTimer() {
    }

    public static synchronized void beginNetworkClose() {
        startedNanos = System.nanoTime();
        networkCloseNanos = -1L;
        teardownStartedNanos = -1L;
        teardownNanos = -1L;
        clearLevelPhaseStartedNanos = -1L;
        clearLevelLastMarkerNanos = -1L;
        VHAccelerator.LOGGER.info("Disconnect timer started");
    }

    public static synchronized void finishNetworkClose() {
        if (startedNanos < 0L || networkCloseNanos >= 0L) {
            return;
        }
        networkCloseNanos = Math.max(0L, System.nanoTime() - startedNanos);
        VHAccelerator.LOGGER.info(
                "Network channel closed in {} ms",
                nanosToMillis(networkCloseNanos)
        );
    }

    public static synchronized void beginClientTeardown() {
        if (startedNanos < 0L) {
            beginNetworkClose();
            finishNetworkClose();
        }
        if (teardownStartedNanos < 0L) {
            teardownStartedNanos = System.nanoTime();
        }
    }

    public static synchronized void finishClientTeardown() {
        if (teardownStartedNanos < 0L || teardownNanos >= 0L) {
            return;
        }
        teardownNanos = Math.max(
                0L,
                System.nanoTime() - teardownStartedNanos
        );
        VHAccelerator.LOGGER.info(
                "Client world teardown completed in {} ms",
                nanosToMillis(teardownNanos)
        );
    }

    /**
     * Temporary diagnostic instrumentation. Remove this phase timer together
     * with the clearLevel injection points once disconnect profiling is done.
     */
    public static synchronized void beginClearLevelPhases(
            int pendingTaskCount
    ) {
        clearLevelPhaseStartedNanos = System.nanoTime();
        clearLevelLastMarkerNanos = clearLevelPhaseStartedNanos;
        VHAccelerator.LOGGER.info(
                "clearLevel diagnostics started [pending tasks={}]",
                pendingTaskCount
        );
    }

    public static synchronized void markClearLevelPhase(String phaseName) {
        markClearLevelPhase(phaseName, null);
    }

    public static synchronized void markClearLevelPhase(
            String phaseName,
            int pendingTaskCount
    ) {
        markClearLevelPhase(phaseName, Integer.valueOf(pendingTaskCount));
    }

    public static synchronized Sample finishMenuTransition(String screenName) {
        if (startedNanos < 0L) {
            return null;
        }

        long totalNanos = Math.max(0L, System.nanoTime() - startedNanos);
        long knownNetworkNanos = Math.max(0L, networkCloseNanos);
        long knownTeardownNanos = Math.max(0L, teardownNanos);
        long menuNanos = Math.max(
                0L,
                totalNanos - knownNetworkNanos - knownTeardownNanos
        );
        Sample sample = new Sample(
                nanosToMillis(totalNanos),
                nanosToMillis(knownNetworkNanos),
                nanosToMillis(knownTeardownNanos),
                nanosToMillis(menuNanos),
                screenName
        );
        lastSample = sample;
        VHAccelerator.LOGGER.info(
                "Disconnect completed in {} ms "
                        + "[network close={} ms, client teardown={} ms, "
                        + "menu transition={} ms, destination={}]",
                sample.totalMillis(),
                sample.networkCloseMillis(),
                sample.clientTeardownMillis(),
                sample.menuTransitionMillis(),
                sample.destinationScreen()
        );
        clearActive();
        return sample;
    }

    public static synchronized Sample lastSample() {
        return lastSample;
    }

    public static synchronized void cancelActive() {
        clearActive();
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    private static void clearActive() {
        startedNanos = -1L;
        networkCloseNanos = -1L;
        teardownStartedNanos = -1L;
        teardownNanos = -1L;
        clearLevelPhaseStartedNanos = -1L;
        clearLevelLastMarkerNanos = -1L;
    }

    private static void markClearLevelPhase(
            String phaseName,
            Integer pendingTaskCount
    ) {
        if (clearLevelPhaseStartedNanos < 0L
                || clearLevelLastMarkerNanos < 0L) {
            return;
        }

        long now = System.nanoTime();
        double phaseMillis = nanosToDecimalMillis(
                Math.max(0L, now - clearLevelLastMarkerNanos)
        );
        double cumulativeMillis = nanosToDecimalMillis(
                Math.max(0L, now - clearLevelPhaseStartedNanos)
        );
        if (pendingTaskCount == null) {
            VHAccelerator.LOGGER.info(
                    "clearLevel phase '{}' completed in {} ms "
                            + "[cumulative={} ms]",
                    phaseName,
                    phaseMillis,
                    cumulativeMillis
            );
        } else {
            VHAccelerator.LOGGER.info(
                    "clearLevel phase '{}' completed in {} ms "
                            + "[cumulative={} ms, pending tasks={}]",
                    phaseName,
                    phaseMillis,
                    cumulativeMillis,
                    pendingTaskCount
            );
        }
        clearLevelLastMarkerNanos = now;
    }

    private static double nanosToDecimalMillis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    public record Sample(
            long totalMillis,
            long networkCloseMillis,
            long clientTeardownMillis,
            long menuTransitionMillis,
            String destinationScreen
    ) {
    }
}
