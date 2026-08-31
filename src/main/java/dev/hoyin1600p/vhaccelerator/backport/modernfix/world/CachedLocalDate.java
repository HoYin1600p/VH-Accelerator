/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted from ModernFix's bat ticking allocation reduction.
 * See THIRD_PARTY_NOTICES.md and docs/MODERNFIX_BACKPORTS.md.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.world;

import java.time.LocalDate;

public final class CachedLocalDate {
    private static final long REFRESH_INTERVAL_MILLIS = 30_000L;
    private static volatile long lastQueryMillis = Long.MIN_VALUE;
    private static volatile LocalDate lastDate;

    private CachedLocalDate() {
    }

    public static LocalDate now() {
        long currentMillis = System.currentTimeMillis();
        LocalDate date = lastDate;
        long age = currentMillis - lastQueryMillis;
        if (date == null || age < 0L || age > REFRESH_INTERVAL_MILLIS) {
            date = LocalDate.now();
            lastQueryMillis = currentMillis;
            lastDate = date;
        }
        return date;
    }
}
