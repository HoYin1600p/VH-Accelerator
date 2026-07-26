package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class LaunchTimer {
    private static final AtomicLong START_NANOS = new AtomicLong(-1L);
    private static final AtomicLong END_NANOS = new AtomicLong(-1L);
    private static final AtomicBoolean CHAT_MESSAGE_CLAIMED = new AtomicBoolean();

    private LaunchTimer() {
    }

    public static void markStart() {
        if (START_NANOS.compareAndSet(-1L, System.nanoTime())) {
            VHAccelerator.LOGGER.info("Client launch timer started");
        }
    }

    public static void markEnd() {
        long start = START_NANOS.get();
        if (start < 0L || !END_NANOS.compareAndSet(-1L, System.nanoTime())) {
            return;
        }

        long elapsedMillis = elapsedMillis();
        VHAccelerator.LOGGER.info(
                "Client launch completed in {} ms ({})",
                elapsedMillis,
                String.format("%.2f seconds", elapsedMillis / 1000.0)
        );
    }

    public static long elapsedMillis() {
        long start = START_NANOS.get();
        if (start < 0L) {
            return -1L;
        }
        long end = END_NANOS.get();
        return ((end < 0L ? System.nanoTime() : end) - start) / 1_000_000L;
    }

    public static boolean isFinished() {
        return END_NANOS.get() >= 0L;
    }

    public static boolean claimChatMessage() {
        return isFinished()
                && VHAcceleratorClientConfig.VALUES.showLaunchTimer.get()
                && CHAT_MESSAGE_CLAIMED.compareAndSet(false, true);
    }
}

