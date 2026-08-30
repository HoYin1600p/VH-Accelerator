/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/ingredient_item_deduplication/IngredientItemValueMixin.java
 * Upstream commit: b26ab375b56d9ec34bb1aa51a8b3cc2f78b2b939
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; replaced MixinExtras with an equivalent defensive-copy injection.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.recipe.dedup;

import java.util.Collection;
import java.util.Collections;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Ingredient.ItemValue.class)
public abstract class IngredientItemValueMixin {
    @Shadow
    @Final
    private ItemStack item;

    @Inject(method = "getItems", at = @At("HEAD"), cancellable = true)
    private void vha$returnDefensiveCopy(
            CallbackInfoReturnable<Collection<ItemStack>> callback
    ) {
        callback.setReturnValue(Collections.singleton(this.item.copy()));
    }
}
