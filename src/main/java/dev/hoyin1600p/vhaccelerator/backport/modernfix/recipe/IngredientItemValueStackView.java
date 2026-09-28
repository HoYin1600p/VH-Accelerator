/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_ingredients/IngredientMixin.java
 * Upstream commit: eed320b05588bcf3df39646701221242c5b5d9bc
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; non-Mixin boundary for the stack held by Forge 40's
 * Ingredient.ItemValue.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe;

import net.minecraft.world.item.ItemStack;

/** Read access to the stack an {@code Ingredient.ItemValue} holds. */
public interface IngredientItemValueStackView {
    ItemStack vha$getStack();
}
