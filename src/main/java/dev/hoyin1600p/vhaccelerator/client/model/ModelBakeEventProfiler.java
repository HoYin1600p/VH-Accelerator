package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * Debug-only attribution for the Forge mod-bus portion of model baking.
 *
 * <p>Forge sends {@link ModelBakeEvent} to every mod container in sequence.
 * Measuring the container dispatches separately identifies the owner of slow
 * callbacks without changing listener order or execution.
 */
public final class ModelBakeEventProfiler {
    private static final long REPORT_THRESHOLD_NANOS = 50_000L;
    private static final List<ModSample> SAMPLES = new ArrayList<>();

    private static boolean active;
    private static long eventStartedNanos;

    private ModelBakeEventProfiler() {
    }

    public static synchronized void begin() {
        active = VHAcceleratorConfig.debugDiagnosticsEnabled();
        SAMPLES.clear();
        eventStartedNanos = active ? System.nanoTime() : -1L;
    }

    public static synchronized long beginContainer(Event event) {
        return active && event instanceof ModelBakeEvent
                ? System.nanoTime()
                : -1L;
    }

    public static synchronized void finishContainer(
            String modId,
            long startedNanos
    ) {
        if (!active || startedNanos < 0L) {
            return;
        }
        SAMPLES.add(new ModSample(
                modId,
                Math.max(0L, System.nanoTime() - startedNanos)
        ));
    }

    public static synchronized void finish() {
        if (!active || eventStartedNanos < 0L) {
            active = false;
            SAMPLES.clear();
            return;
        }

        long totalNanos = Math.max(
                0L,
                System.nanoTime() - eventStartedNanos
        );
        long containerNanos = SAMPLES.stream()
                .mapToLong(ModSample::elapsedNanos)
                .sum();
        long residualNanos = Math.max(0L, totalNanos - containerNanos);

        VHAccelerator.LOGGER.info(
                "Forge ModelBakeEvent attribution: {} mod container(s), "
                        + "{} ms total, {} ms in container dispatch, "
                        + "{} ms in Forge post-bake/dispatch overhead",
                SAMPLES.size(),
                formatMillis(totalNanos),
                formatMillis(containerNanos),
                formatMillis(residualNanos)
        );

        SAMPLES.stream()
                .filter(sample ->
                        sample.elapsedNanos() >= REPORT_THRESHOLD_NANOS)
                .sorted(Comparator.comparingLong(
                        ModSample::elapsedNanos
                ).reversed())
                .forEach(sample -> VHAccelerator.LOGGER.info(
                        "Forge ModelBakeEvent owner {}: {} ms",
                        sample.modId(),
                        formatMillis(sample.elapsedNanos())
                ));

        active = false;
        eventStartedNanos = -1L;
        SAMPLES.clear();
    }

    private static String formatMillis(long nanos) {
        return String.format(
                Locale.ROOT,
                "%.3f",
                nanos / 1_000_000.0
        );
    }

    private record ModSample(String modId, long elapsedNanos) {
    }
}
