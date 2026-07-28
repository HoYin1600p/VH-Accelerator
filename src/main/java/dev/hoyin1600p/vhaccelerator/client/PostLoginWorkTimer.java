package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Measures every tracked login job that remains after the first playable
 * frame. A session can own multiple work tokens, including delayed player-head
 * profile callbacks started while JEI builds its search index.
 */
public final class PostLoginWorkTimer {
    private static long nextToken;
    private static long activeSession = -1L;
    private static long firstPlayableFrameNanos = -1L;
    private static long workCompletedNanos = -1L;
    private static boolean finalized;
    private static final Map<Long, String> ACTIVE_WORK =
            new LinkedHashMap<>();
    private static Sample lastSample;
    private static Sample unannouncedSample;

    private PostLoginWorkTimer() {
    }

    public static synchronized void beginSession(long session) {
        if (!VHAcceleratorConfig.instrumentationEnabled()) {
            clearActive();
            return;
        }
        activeSession = session;
        firstPlayableFrameNanos = -1L;
        workCompletedNanos = -1L;
        finalized = false;
        ACTIVE_WORK.clear();
        unannouncedSample = null;
        VHAccelerator.LOGGER.info(
                "Post-login work timer started for session {}",
                activeSession
        );
    }

    public static synchronized long markWorkStarted(
            long session,
            String description
    ) {
        if (!VHAcceleratorConfig.instrumentationEnabled()) {
            clearActive();
            return -1L;
        }
        if (session < 0L || session != activeSession || finalized) {
            return -1L;
        }
        long token = ++nextToken;
        ACTIVE_WORK.put(token, description);
        workCompletedNanos = -1L;
        return token;
    }

    public static synchronized long markAuxiliaryWorkStarted(
            long session,
            String description
    ) {
        if (session < 0L || session != activeSession || finalized) {
            return -1L;
        }
        return markWorkStarted(session, description);
    }

    public static synchronized void markFirstPlayableFrame() {
        if (!VHAcceleratorConfig.instrumentationEnabled()) {
            clearActive();
            return;
        }
        if (activeSession < 0L || firstPlayableFrameNanos >= 0L) {
            return;
        }
        firstPlayableFrameNanos = System.nanoTime();
        if (ACTIVE_WORK.isEmpty() && workCompletedNanos < 0L) {
            workCompletedNanos = firstPlayableFrameNanos;
        }
        finishIfReady();
    }

    public static synchronized void markWorkCompleted(long token) {
        if (!VHAcceleratorConfig.instrumentationEnabled()) {
            clearActive();
            return;
        }
        if (token < 0L || ACTIVE_WORK.remove(token) == null) {
            return;
        }
        if (ACTIVE_WORK.isEmpty()) {
            workCompletedNanos = System.nanoTime();
        }
        finishIfReady();
    }

    public static synchronized void cancel(long token) {
        if (token >= 0L && ACTIVE_WORK.remove(token) != null
                && ACTIVE_WORK.isEmpty()) {
            workCompletedNanos = System.nanoTime();
            finishIfReady();
        }
    }

    public static synchronized void cancelSession(long session) {
        if (session == activeSession) {
            if (!ACTIVE_WORK.isEmpty()) {
                VHAccelerator.LOGGER.info(
                        "Cancelling {} outstanding post-login job(s) for "
                                + "session {}: {}",
                        ACTIVE_WORK.size(),
                        session,
                        String.join(", ", ACTIVE_WORK.values())
                );
            }
            clearActive();
        }
    }

    public static synchronized boolean isRunning() {
        return activeSession >= 0L && !finalized;
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
        if (finalized
                || firstPlayableFrameNanos < 0L
                || workCompletedNanos < 0L
                || !ACTIVE_WORK.isEmpty()) {
            return;
        }

        long elapsedNanos = Math.max(0L, workCompletedNanos - firstPlayableFrameNanos);
        Sample sample = new Sample(activeSession, elapsedNanos / 1_000_000L);
        lastSample = sample;
        unannouncedSample = sample;
        finalized = true;
        VHAccelerator.LOGGER.info(
                "Post-login work completed in {} ms ({})",
                sample.totalMillis(),
                String.format("%.2f seconds", sample.totalMillis() / 1000.0)
        );
    }

    private static void clearActive() {
        activeSession = -1L;
        firstPlayableFrameNanos = -1L;
        workCompletedNanos = -1L;
        finalized = false;
        ACTIVE_WORK.clear();
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
