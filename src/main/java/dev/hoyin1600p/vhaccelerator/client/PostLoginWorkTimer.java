package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;

/**
 * Measures the work that remains after the first playable world frame. JEI
 * startup owns the current work token, so stale transfer generations cannot
 * complete a newer sample.
 */
public final class PostLoginWorkTimer {
    private static long nextToken;
    private static long activeToken = -1L;
    private static long firstPlayableFrameNanos = -1L;
    private static long workCompletedNanos = -1L;
    private static Sample lastSample;
    private static Sample unannouncedSample;

    private PostLoginWorkTimer() {
    }

    public static synchronized long markWorkStarted() {
        activeToken = ++nextToken;
        firstPlayableFrameNanos = -1L;
        workCompletedNanos = -1L;
        unannouncedSample = null;
        VHAccelerator.LOGGER.info(
                "Post-login work timer started for generation {}",
                activeToken
        );
        return activeToken;
    }

    public static synchronized void markFirstPlayableFrame() {
        if (activeToken < 0L || firstPlayableFrameNanos >= 0L) {
            return;
        }
        firstPlayableFrameNanos = System.nanoTime();
        finishIfReady();
    }

    public static synchronized void markWorkCompleted(long token) {
        if (token != activeToken || workCompletedNanos >= 0L) {
            return;
        }
        workCompletedNanos = System.nanoTime();
        finishIfReady();
    }

    public static synchronized void cancel(long token) {
        if (token == activeToken) {
            clearActive();
        }
    }

    public static synchronized void cancelActive() {
        clearActive();
    }

    public static synchronized boolean isRunning() {
        return activeToken >= 0L && unannouncedSample == null;
    }

    public static synchronized Sample claimCompletedSample() {
        Sample sample = unannouncedSample;
        unannouncedSample = null;
        return sample;
    }

    public static synchronized Sample lastSample() {
        return lastSample;
    }

    private static void finishIfReady() {
        if (firstPlayableFrameNanos < 0L || workCompletedNanos < 0L) {
            return;
        }

        long elapsedNanos = Math.max(0L, workCompletedNanos - firstPlayableFrameNanos);
        Sample sample = new Sample(activeToken, elapsedNanos / 1_000_000L);
        lastSample = sample;
        unannouncedSample = sample;
        VHAccelerator.LOGGER.info(
                "Post-login work completed in {} ms ({})",
                sample.totalMillis(),
                String.format("%.2f seconds", sample.totalMillis() / 1000.0)
        );
        activeToken = -1L;
        firstPlayableFrameNanos = -1L;
        workCompletedNanos = -1L;
    }

    private static void clearActive() {
        activeToken = -1L;
        firstPlayableFrameNanos = -1L;
        workCompletedNanos = -1L;
        unannouncedSample = null;
    }

    public static final class Sample {
        private final long generation;
        private final long totalMillis;

        private Sample(long generation, long totalMillis) {
            this.generation = generation;
            this.totalMillis = totalMillis;
        }

        public long generation() {
            return generation;
        }

        public long totalMillis() {
            return totalMillis;
        }
    }
}
