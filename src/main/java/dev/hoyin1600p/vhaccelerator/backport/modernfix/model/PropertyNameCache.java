/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/dedup/IdentifierCaches.java
 * Upstream commit: fe855f15304ed788122a27cda4c2495a78374528
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; reduced the cache to block-property names and added focused tests.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.model;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;

public final class PropertyNameCache {
    private static final Map<String, String> NAMES =
            new Object2ObjectOpenHashMap<>();

    private PropertyNameCache() {
    }

    public static synchronized String deduplicate(String name) {
        String existing = NAMES.get(name);
        if (existing != null) {
            return existing;
        }
        NAMES.put(name, name);
        return name;
    }

    static synchronized void clearForTest() {
        NAMES.clear();
    }
}
