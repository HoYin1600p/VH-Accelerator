/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/attribute_supplier_dedup/AttributeSupplierBuilderMixin.java
 * Related upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/core/GameDataMixin.java
 * Upstream commit: 37dc9e60eb0f08c788caa5f49a6cb6bc9c0c8bf0
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-29; replaced the newer Forge registry hook with a Forge 1.18.2 load-complete latch.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.entity;

public final class AttributeSupplierDeduplication {
    private static volatile boolean startupWindowOpen = true;

    private AttributeSupplierDeduplication() {
    }

    public static boolean startupWindowOpen() {
        return startupWindowOpen;
    }

    public static void closeStartupWindow() {
        startupWindowOpen = false;
    }
}
