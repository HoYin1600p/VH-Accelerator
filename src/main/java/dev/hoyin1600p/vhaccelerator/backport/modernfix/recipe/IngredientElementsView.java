/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/forge/recipe/ExtendedIngredient.java
 * Upstream commit: eed320b05588bcf3df39646701221242c5b5d9bc
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; exposes Forge 40's Ingredient fields instead of a
 * computed result, so the element count lives outside the mixin.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe;

import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Read access to a vanilla {@code Ingredient}'s values and expansion state. */
public interface IngredientElementsView {
    Ingredient.Value[] vha$getValues();

    @Nullable
    ItemStack[] vha$getItemStacks();
}
