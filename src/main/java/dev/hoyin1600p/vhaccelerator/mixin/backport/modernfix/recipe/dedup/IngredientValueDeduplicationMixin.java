/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/ingredient_item_deduplication/IngredientMixin.java
 * Upstream commit: b26ab375b56d9ec34bb1aa51a8b3cc2f78b2b939
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted constructor-stream deduplication to Forge 40.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.recipe.dedup;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe.IngredientValueDeduplicator;
import java.util.stream.Stream;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Ingredient.class)
public abstract class IngredientValueDeduplicationMixin {
    @ModifyVariable(
            method = "<init>",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static Stream<? extends Ingredient.Value>
            vha$deduplicateItemValues(
                    Stream<? extends Ingredient.Value> values
            ) {
        return values.map(IngredientValueDeduplicator::deduplicate);
    }
}
