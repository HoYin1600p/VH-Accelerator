package dev.hoyin1600p.vhaccelerator.client.compat.crafttweaker;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;

/**
 * Tracks CraftTweaker's synchronized client script replay and compacts only
 * its repetitive per-action INFO messages. Validation warnings, errors, file
 * loading messages, and lifecycle messages still use CraftTweaker's logger.
 */
public final class CraftTweakerClientReplay {
    private static final ThreadLocal<Timing> ACTIVE = new ThreadLocal<>();

    private CraftTweakerClientReplay() {
    }

    public static void begin() {
        if (!enabled() || ACTIVE.get() != null) {
            return;
        }
        ACTIVE.set(new Timing(System.nanoTime()));
    }

    public static void finish() {
        Timing timing = ACTIVE.get();
        if (timing == null) {
            return;
        }
        ACTIVE.remove();

        long total = System.nanoTime() - timing.startedNanos;
        long semantic = Math.max(
                0L,
                timing.scriptRunNanos - timing.executionNanos
        );
        VHAccelerator.LOGGER.info(
                "CraftTweaker synchronized client replay completed in {} ms "
                        + "[sources {} ms, modules {} ms, semantic {} ms, "
                        + "execution {} ms, compacted {} action log lines]",
                formatMillis(total),
                formatMillis(timing.sourcePreparationNanos),
                formatMillis(timing.moduleInitializationNanos),
                formatMillis(semantic),
                formatMillis(timing.executionNanos),
                timing.compactedActionLogs
        );
    }

    public static boolean compactActionLogs() {
        return ACTIVE.get() != null;
    }

    public static void recordCompactedActionLog() {
        Timing timing = ACTIVE.get();
        if (timing != null) {
            timing.compactedActionLogs++;
        }
    }

    public static void beginSourcePreparation() {
        Timing timing = ACTIVE.get();
        if (timing != null) {
            timing.sourcePreparationStarted = System.nanoTime();
        }
    }

    public static void finishSourcePreparation() {
        Timing timing = ACTIVE.get();
        if (timing != null && timing.sourcePreparationStarted >= 0L) {
            timing.sourcePreparationNanos +=
                    System.nanoTime() - timing.sourcePreparationStarted;
            timing.sourcePreparationStarted = -1L;
        }
    }

    public static void beginModuleInitialization() {
        Timing timing = ACTIVE.get();
        if (timing != null) {
            timing.moduleInitializationStarted = System.nanoTime();
        }
    }

    public static void finishModuleInitialization() {
        Timing timing = ACTIVE.get();
        if (timing != null && timing.moduleInitializationStarted >= 0L) {
            timing.moduleInitializationNanos +=
                    System.nanoTime() - timing.moduleInitializationStarted;
            timing.moduleInitializationStarted = -1L;
        }
    }

    public static void beginScriptRun() {
        Timing timing = ACTIVE.get();
        if (timing != null) {
            timing.scriptRunStarted = System.nanoTime();
        }
    }

    public static void finishScriptRun() {
        Timing timing = ACTIVE.get();
        if (timing != null && timing.scriptRunStarted >= 0L) {
            timing.scriptRunNanos +=
                    System.nanoTime() - timing.scriptRunStarted;
            timing.scriptRunStarted = -1L;
        }
    }

    public static void beginExecution() {
        Timing timing = ACTIVE.get();
        if (timing != null) {
            timing.executionStarted = System.nanoTime();
        }
    }

    public static void finishExecution() {
        Timing timing = ACTIVE.get();
        if (timing != null && timing.executionStarted >= 0L) {
            timing.executionNanos +=
                    System.nanoTime() - timing.executionStarted;
            timing.executionStarted = -1L;
        }
    }

    private static boolean enabled() {
        return VHAcceleratorClientConfig.VALUES
                .enableClientOptimizations.get()
                && VHAcceleratorClientConfig.VALUES
                        .compactCraftTweakerClientReplayLogging.get();
    }

    private static String formatMillis(long nanos) {
        return String.format(
                java.util.Locale.ROOT,
                "%.3f",
                nanos / 1_000_000.0
        );
    }

    private static final class Timing {
        private final long startedNanos;
        private long sourcePreparationStarted = -1L;
        private long sourcePreparationNanos;
        private long moduleInitializationStarted = -1L;
        private long moduleInitializationNanos;
        private long scriptRunStarted = -1L;
        private long scriptRunNanos;
        private long executionStarted = -1L;
        private long executionNanos;
        private int compactedActionLogs;

        private Timing(long startedNanos) {
            this.startedNanos = startedNanos;
        }
    }
}
