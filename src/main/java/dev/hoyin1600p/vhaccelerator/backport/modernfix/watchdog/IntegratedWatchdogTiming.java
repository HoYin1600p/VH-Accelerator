/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/world/IntegratedWatchdog.java
 * Upstream commit: bc7aa5539c351d65e1e5b2b87b7c767367f4be52
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; extracted testable boot and timeout timing rules from commits 3bad8f59 and bc7aa553.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.watchdog;

public final class IntegratedWatchdogTiming {
    public static final long CHECK_INTERVAL_MILLIS = 40_000L;
    public static final long SERVER_BOOT_RETRY_MILLIS = 10_000L;

    private IntegratedWatchdogTiming() {
    }

    public static boolean serverIsBooting(long lastTickStart) {
        return lastTickStart < 0L;
    }

    public static long safeSleepDelay(long requestedDelay) {
        return requestedDelay <= 0L
                ? CHECK_INTERVAL_MILLIS
                : requestedDelay;
    }
}
