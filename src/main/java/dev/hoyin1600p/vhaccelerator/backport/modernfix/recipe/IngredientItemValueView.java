/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/forge/recipe/IngredientValueDeduplicator.java
 * Upstream commit: b26ab375b56d9ec34bb1aa51a8b3cc2f78b2b939
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; split the stack view out of the accessor mixin so logic does not import mixin packages.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe;

import net.minecraft.world.item.ItemStack;

/**
 * Read access to the stack held by a Forge 40 {@code Ingredient.ItemValue}.
 * The accessor mixin for that class extends this interface, so logic can cast
 * the value to it without depending on the mixin package.
 */
public interface IngredientItemValueView {
    ItemStack vha$getItem();
}
