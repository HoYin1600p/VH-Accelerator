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
import net.minecraft.resources.ResourceLocation;

/**
 * Attributes Forge's synchronous registry launch work without changing event
 * ordering, executors, or exception handling.
 */
public final class RegistryLaunchProfiler {
    private static final Map<String, Timing> EVENT_TIMINGS =
            new ConcurrentHashMap<>();
    private static final Map<String, Timing> HOLDER_TIMINGS =
            new ConcurrentHashMap<>();
    private static final AtomicBoolean FINISHED = new AtomicBoolean();

    private RegistryLaunchProfiler() {
    }

    public static long begin() {
        return enabled() ? System.nanoTime() : 0L;
    }

    public static void recordEvent(
            String modId,
            ResourceLocation registryName,
            long startedNanos
    ) {
        record(
                EVENT_TIMINGS,
                modId + " -> " + registryName,
                startedNanos
        );
    }

    public static void recordHolderLookup(
            ResourceLocation registryName,
            long startedNanos
    ) {
        record(
                HOLDER_TIMINGS,
                String.valueOf(registryName),
                startedNanos
        );
    }

    public static void finish() {
        if (!FINISHED.compareAndSet(false, true)) {
            return;
        }
        if (VHAcceleratorClientConfig.launchProfilingEnabled()) {
            report("registry callback", EVENT_TIMINGS, 20);
            report("object-holder registry", HOLDER_TIMINGS, 20);
        }
        EVENT_TIMINGS.clear();
        HOLDER_TIMINGS.clear();
    }

    private static boolean enabled() {
        return !FINISHED.get()
                && !LaunchTimer.isFinished()
                && VHAcceleratorClientConfig.launchProfilingEnabled();
    }

    private static void record(
            Map<String, Timing> timings,
            String key,
            long startedNanos
    ) {
        if (startedNanos == 0L || FINISHED.get()) {
            return;
        }
        long elapsed = System.nanoTime() - startedNanos;
        if (elapsed <= 0L) {
            return;
        }
        timings.computeIfAbsent(key, ignored -> new Timing()).add(elapsed);
    }

    private static void report(
            String label,
            Map<String, Timing> timings,
            int limit
    ) {
        List<Map.Entry<String, Timing>> entries =
                new ArrayList<>(timings.entrySet());
        entries.sort(Comparator.comparingLong(
                (Map.Entry<String, Timing> entry) ->
                        entry.getValue().nanos.sum()
        ).reversed());
        int displayed = Math.min(limit, entries.size());
        for (int index = 0; index < displayed; index++) {
            Map.Entry<String, Timing> entry = entries.get(index);
            Timing timing = entry.getValue();
            VHAccelerator.LOGGER.info(
                    "Forge launch {} [{}] {}: {} ms across {} call(s)",
                    label,
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
