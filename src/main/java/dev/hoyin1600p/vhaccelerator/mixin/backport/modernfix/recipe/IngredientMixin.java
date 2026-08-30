/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/mixin/perf/faster_ingredients/IngredientMixin.java
 * Upstream commit: 5048e74c79724029a8ba2e3d7cdd37c83320ad4e
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted only the allocation-free tag test and
 * stacking-ID paths to Minecraft 1.18.2. Ingredient arrays remain vanilla.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.recipe;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe.IngredientReloadTracker;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe.IngredientTagValueView;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Ingredient.class, priority = 700)
public abstract class IngredientMixin {
    @Shadow(remap = false)
    public abstract boolean isVanilla();

    @Shadow
    @Final
    private Ingredient.Value[] values;

    @Shadow
    @Nullable
    private IntList stackingIds;

    /**
     * @author embeddedt, HoYin1600p
     * @reason A single vanilla tag ingredient can test registry membership
     * directly without expanding the complete tag into ItemStack objects.
     */
    @Inject(
            method = "test(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/Ingredient;dissolve()V"
            ),
            cancellable = true
    )
    private void vha$testSingleTagWithoutExpansion(
            @Nullable ItemStack stack,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Optional<HolderSet.Named<Item>> tag = vha$singleBoundTag();
        if (tag.isPresent() && tag.get().size() > 0) {
            TagKey<Item> key = ((IngredientTagValueView) values[0]).vha$getTag();
            cir.setReturnValue(stack != null && stack.is(key));
        }
    }

    /**
     * @author embeddedt, HoYin1600p
     * @reason A single vanilla tag ingredient can derive its sorted stacking
     * IDs directly from the item registry without allocating ItemStacks.
     */
    @Inject(
            method = "getStackingIds",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/Ingredient;dissolve()V"
            ),
            cancellable = true
    )
    private void vha$stackSingleTagWithoutExpansion(
            CallbackInfoReturnable<IntList> cir
    ) {
        Optional<HolderSet.Named<Item>> tag = vha$singleBoundTag();
        if (tag.isEmpty() || tag.get().size() == 0) {
            return;
        }

        IntArrayList ids = new IntArrayList(tag.get().size());
        tag.get().forEach(holder -> ids.add(Registry.ITEM.getId(holder.value())));
        ids.sort(IntComparators.NATURAL_COMPARATOR);
        this.stackingIds = ids;
        cir.setReturnValue(ids);
    }

    private Optional<HolderSet.Named<Item>> vha$singleBoundTag() {
        if (!this.isVanilla()
                || IngredientReloadTracker.active()
                || this.values.length != 1
                || !(this.values[0] instanceof Ingredient.TagValue)) {
            return Optional.empty();
        }
        TagKey<Item> key = ((IngredientTagValueView) this.values[0]).vha$getTag();
        return Registry.ITEM.getTag(key);
    }
}
