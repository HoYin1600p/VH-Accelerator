/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/util/SharedSystemInfo.java
 * Upstream commit: 64ce25a1607e74f8e4c8972512774f124ef460dc
 * Original copyright: Copyright (c) 2026; embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-11-27; adapted lazy shared oshi.SystemInfo holder for VH Accelerator 1.18.2.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.systeminfo;

import oshi.SystemInfo;

/**
 * Shares one lazily constructed OSHI {@link SystemInfo} between GLX initialization and
 * {@code SystemReport}, avoiding redundant platform/WMI hardware probes.
 */
public final class SharedSystemInfo {
    private static volatile SystemInfo instance;

    private SharedSystemInfo() {
    }

    public static SystemInfo get() {
        SystemInfo current = instance;
        if (current != null) {
            return current;
        }
        synchronized (SharedSystemInfo.class) {
            current = instance;
            if (current == null) {
                current = new SystemInfo();
                instance = current;
            }
            return current;
        }
    }
}
