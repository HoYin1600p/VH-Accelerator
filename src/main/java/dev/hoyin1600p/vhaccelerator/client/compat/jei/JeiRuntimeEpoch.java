package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.ConnectionEpoch;

/** A replaced/stopped JEI runtime may never receive an older worker's publication. */
public final class JeiRuntimeEpoch {
    private static final ConnectionEpoch EPOCH = new ConnectionEpoch();
    private static Object owner;
    private static volatile Runnable afterStart = () -> { };

    private JeiRuntimeEpoch() { }

    public static synchronized long current() { return EPOCH.current(); }
    public static synchronized boolean isCurrent(long generation) { return EPOCH.isCurrent(generation); }

    /** Runs after each JEI runtime start completes; never fails the start. */
    public static void setAfterStart(Runnable action) {
        afterStart = action;
    }

    public static synchronized void invalidate() {
        EPOCH.close(owner);
        owner = null;
    }

    public static Runnable wrapStart(Runnable action) {
        return () -> {
            Object started = new Object();
            synchronized (JeiRuntimeEpoch.class) {
                owner = started;
                EPOCH.observe(started);
            }
            try { action.run(); }
            catch (RuntimeException | Error failure) {
                synchronized (JeiRuntimeEpoch.class) { EPOCH.close(started); }
                throw failure;
            }
            try { afterStart.run(); }
            catch (RuntimeException failure) {
                VHAccelerator.LOGGER.warn("Post-start JEI cleanup failed", failure);
            }
        };
    }

    public static Runnable wrapStop(Runnable action) {
        return () -> { invalidate(); action.run(); };
    }
}
