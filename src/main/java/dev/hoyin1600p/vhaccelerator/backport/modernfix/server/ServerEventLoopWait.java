/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/fix_loop_spin_waiting/MinecraftServerMixin.java
 * Upstream commit: a643170426cfe57cdfffddde87229b5506c49de5
 * Original copyright: Copyright (c) 2025 embeddedt, HaHaWTH, and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; extracted and bounded the monotonic tick-deadline calculation.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.server;

public final class ServerEventLoopWait {
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    private ServerEventLoopWait() {
    }

    public static long remainingNanos(
            long nextTickTimeMillis,
            long currentTimeNanos
    ) {
        return Math.max(
                0L,
                nextTickTimeMillis * NANOS_PER_MILLISECOND
                        - currentTimeNanos
        );
    }
}
