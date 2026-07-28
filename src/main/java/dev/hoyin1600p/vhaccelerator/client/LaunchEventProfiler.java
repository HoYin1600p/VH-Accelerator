package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Measures reachable Forge client-loading phases without changing their
 * executors, task order, or error handling.
 */
public final class LaunchEventProfiler {
    private static final Map<String, Timing> STAGE_TIMINGS =
            new ConcurrentHashMap<>();
    private static final AtomicBoolean FINISHED =
            new AtomicBoolean();

    private LaunchEventProfiler() {
    }

    public static long beginStage() {
        return enabled() ? System.nanoTime() : 0L;
    }

    public static void finishStage(
            String stage,
            long startedNanos
    ) {
        if (startedNanos == 0L || FINISHED.get()) {
            return;
        }
        long elapsed = System.nanoTime() - startedNanos;
        if (elapsed <= 0L) {
            return;
        }
        STAGE_TIMINGS.computeIfAbsent(
                stage,
                ignored -> new Timing()
        ).add(elapsed);
    }

    public static void finish() {
        if (!FINISHED.compareAndSet(false, true)) {
            return;
        }

        reportStages();
        STAGE_TIMINGS.clear();
    }

    public static boolean enabled() {
        return !FINISHED.get()
                && !LaunchTimer.isFinished()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .profileClientLaunchPhases
                );
    }

    private static void reportStages() {
        if (STAGE_TIMINGS.isEmpty()) {
            VHAccelerator.LOGGER.debug(
                    "Forge client-loading profiler observed no launch phases"
            );
            return;
        }

        List<Map.Entry<String, Timing>> stages =
                new ArrayList<>(STAGE_TIMINGS.entrySet());
        stages.sort(Comparator.comparingLong(
                (Map.Entry<String, Timing> entry) ->
                        entry.getValue().nanos.sum()
        ).reversed());
        for (int index = 0; index < stages.size(); index++) {
            Map.Entry<String, Timing> entry = stages.get(index);
            Timing timing = entry.getValue();
            VHAccelerator.LOGGER.info(
                    "Forge client-loading phase [{}] {}: {} ms total "
                            + "across {} call(s)",
                    index + 1,
                    entry.getKey(),
                    millis(timing.nanos.sum()),
                    timing.calls.sum()
            );
        }
    }

    private static String millis(long nanos) {
        return String.format(
                Locale.ROOT,
                "%.1f",
                nanos / 1_000_000.0
        );
    }

    private static final class Timing {
        private final LongAdder nanos = new LongAdder();
        private final LongAdder calls = new LongAdder();

        private void add(long elapsed) {
            nanos.add(elapsed);
            calls.increment();
        }
    }
}
