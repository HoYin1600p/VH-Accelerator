package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.client.model.ParallelBlockStateJsonParser;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class LaunchTimer {
    private static final AtomicLong START_NANOS = new AtomicLong(-1L);
    private static final AtomicLong END_NANOS = new AtomicLong(-1L);
    private static final AtomicBoolean CHAT_MESSAGE_CLAIMED = new AtomicBoolean();

    private LaunchTimer() {
    }

    public static void markStart() {
        long attachedAtNanos = System.nanoTime();
        long processUptimeMillis = Math.max(
                0L,
                ManagementFactory.getRuntimeMXBean().getUptime()
        );
        long processStartNanos = attachedAtNanos
                - TimeUnit.MILLISECONDS.toNanos(processUptimeMillis);
        if (START_NANOS.compareAndSet(-1L, processStartNanos)) {
            if (VHAcceleratorConfig.instrumentationEnabled()) {
                VHAccelerator.LOGGER.info(
                        "Client launch timer attached {} ms after JVM "
                                + "process start",
                        processUptimeMillis
                );
            }
        }
    }

    public static void markEnd() {
        long start = START_NANOS.get();
        if (start < 0L || !END_NANOS.compareAndSet(-1L, System.nanoTime())) {
            return;
        }

        long elapsedMillis = elapsedMillis();
        LaunchEventProfiler.finish();
        if (VHAcceleratorConfig.instrumentationEnabled()) {
            VHAccelerator.LOGGER.info(
                    "Client launch completed in {} ms ({})",
                    elapsedMillis,
                    String.format(
                            "%.2f seconds",
                            elapsedMillis / 1000.0
                    )
            );
        }
        ParallelBlockStateJsonParser.releaseLaunchSessions();
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
                && VHAcceleratorConfig.timersEnabled()
                && CHAT_MESSAGE_CLAIMED.compareAndSet(false, true);
    }
}
