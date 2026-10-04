package dev.hoyin1600p.vhaccelerator.util;

import java.util.Locale;

/** Nanosecond to millisecond conversions shared by the timing logs. */
public final class TimeFormat {
    private TimeFormat() {
    }

    /** Whole milliseconds, truncated. */
    public static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    /** Milliseconds with three decimals, always using a dot separator. */
    public static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }
}
