/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/forge/recipe/ExtendedIngredient.java
 * Upstream commit: 5048e74c79724029a8ba2e3d7cdd37c83320ad4e
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; reduced the extension contract to identity-safe soft-cache cleanup.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe;

public interface IngredientExpansionCacheOwner {
    void vha$clearExpansionReference(
            IngredientItemStacksSoftReference expected
    );
}
