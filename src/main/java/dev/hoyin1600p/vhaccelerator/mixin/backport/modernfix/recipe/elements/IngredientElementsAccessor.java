/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_ingredients/IngredientMixin.java
 * Upstream commit: eed320b05588bcf3df39646701221242c5b5d9bc
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; exposes the Forge 40 fields the upstream mixin shadows.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.recipe.elements;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe.IngredientElementsView;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Ingredient.class)
public abstract class IngredientElementsAccessor implements IngredientElementsView {
    @Shadow
    @Final
    private Ingredient.Value[] values;

    @Shadow
    @Nullable
    private ItemStack[] itemStacks;

    @Override
    public Ingredient.Value[] vha$getValues() {
        return this.values;
    }

    @Override
    @Nullable
    public ItemStack[] vha$getItemStacks() {
        return this.itemStacks;
    }
}
