package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.util.Locale;
import net.minecraft.client.resources.model.ModelResourceLocation;

public final class PlaceboItemMappingProfiler {
    private static boolean enabled;
    private static long started;
    private static ModelResourceLocation original;
    private static long totalNanos;
    private static int calls;
    private static int remapped;

    private PlaceboItemMappingProfiler() {
    }

    public static void beginSession(boolean shouldProfile) {
        enabled = shouldProfile;
        started = 0L;
        original = null;
        totalNanos = 0L;
        calls = 0;
        remapped = 0;
    }

    public static void begin(ModelResourceLocation input) {
        if (!enabled) {
            return;
        }
        original = input;
        started = System.nanoTime();
    }

    public static void end(ModelResourceLocation result) {
        if (!enabled || started == 0L) {
            return;
        }
        totalNanos += System.nanoTime() - started;
        calls++;
        if (result != original) {
            remapped++;
        }
        started = 0L;
        original = null;
    }

    public static void reportAndReset() {
        if (enabled && calls > 0) {
            VHAccelerator.LOGGER.info(
                    "Placebo item model mapping: {} ms across {} item(s), "
                            + "{} remapped",
                    String.format(
                            Locale.ROOT,
                            "%.1f",
                            totalNanos / 1_000_000.0
                    ),
                    calls,
                    remapped
            );
        }
        beginSession(false);
    }
}
