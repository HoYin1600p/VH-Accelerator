/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/core/WorldLoaderMixin.java
 * Upstream commit: 5048e74c79724029a8ba2e3d7cdd37c83320ad4e
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted the reload guard to Minecraft 1.18.2's
 * ReloadableServerResources lifecycle and made nested reload tracking atomic.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe;

import java.util.concurrent.atomic.AtomicInteger;

public final class IngredientReloadTracker {
    private static final AtomicInteger ACTIVE_RELOADS = new AtomicInteger();

    private IngredientReloadTracker() {
    }

    public static void begin() {
        ACTIVE_RELOADS.incrementAndGet();
    }

    public static void finish() {
        ACTIVE_RELOADS.updateAndGet(active -> Math.max(0, active - 1));
    }

    public static boolean active() {
        return ACTIVE_RELOADS.get() > 0;
    }

    static int activeCount() {
        return ACTIVE_RELOADS.get();
    }

    static void resetForTest() {
        ACTIVE_RELOADS.set(0);
    }
}
