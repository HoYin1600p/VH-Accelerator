/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/ticking_chunk_alloc/BatMixin.java
 * Upstream commit: 070b7b6d126af5d04cb6a4315d823a2ad6dbc6bb
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: moved cached state outside the mixin and made
 * benign cross-thread publication explicit with volatile fields.
 * Modified: 2026-08-30
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
