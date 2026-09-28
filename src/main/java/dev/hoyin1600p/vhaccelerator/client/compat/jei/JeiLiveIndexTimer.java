package dev.hoyin1600p.vhaccelerator.client.compat.jei;

/** Debug: start time of JEI's live indexing of the current recipe batch. */
public final class JeiLiveIndexTimer {
    private static final ThreadLocal<long[]> STARTED = ThreadLocal.withInitial(() -> new long[] {-1L});

    private JeiLiveIndexTimer() {
    }

    public static void start() {
        STARTED.get()[0] = System.nanoTime();
    }

    /** Nanoseconds since {@link #start()}, or -1 when not started; clears the start. */
    public static long finish() {
        long[] started = STARTED.get();
        if (started[0] < 0L) {
            return -1L;
        }
        long nanos = System.nanoTime() - started[0];
        started[0] = -1L;
        return nanos;
    }
}
