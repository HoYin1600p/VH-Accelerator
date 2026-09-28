/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_ingredients/ForgeHooksMixin.java
 * Upstream commit: eed320b05588bcf3df39646701221242c5b5d9bc (introduction)
 *                  b26ab375b56d9ec34bb1aa51a8b3cc2f78b2b939 (current location)
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; delegates to an exact Forge 40 element count that can
 * decline, instead of upstream's unconditional vanilla-ingredient override.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.recipe.elements;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe.IngredientElements;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Answers {@code ForgeHooks#hasNoElements} without expanding vanilla
 * ingredients; see {@link IngredientElements} for the exact equivalence.
 */
@Mixin(value = ForgeHooks.class, priority = 900)
public final class ForgeHooksIngredientElementsMixin {
    /**
     * @author embeddedt, HoYin1600p
     * @reason Expanding every ingredient into ItemStacks is unnecessary to
     * decide whether it has elements.
     */
    @Inject(method = "hasNoElements", at = @At("HEAD"), cancellable = true, remap = false)
    private static void vha$countElementsWithoutExpansion(
            Ingredient ingredient,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Boolean answer = IngredientElements.hasNoElements(ingredient);
        if (answer != null) {
            cir.setReturnValue(answer);
        }
    }
}
