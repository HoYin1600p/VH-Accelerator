/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_ingredients/IngredientMixin.java
 * Upstream commit: 5048e74c79724029a8ba2e3d7cdd37c83320ad4e
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted soft ingredient expansion caching to Forge 40,
 * preserved explicit mod-provided arrays, and made invalidation eager and identity-safe.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.recipe;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe.IngredientExpansionCacheOwner;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe.IngredientItemStacksSoftReference;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe.IngredientReloadTracker;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe.IngredientTagValueView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Ingredient.class, priority = 690)
public abstract class IngredientExpansionCacheMixin
        implements IngredientExpansionCacheOwner {
    @Shadow
    @Final
    private Ingredient.Value[] values;

    @Shadow
    @Nullable
    private ItemStack[] itemStacks;

    @Shadow(remap = false)
    public abstract boolean checkInvalidation();

    @Shadow(remap = false)
    protected abstract void markValid();

    @Unique
    private volatile IngredientItemStacksSoftReference
            vha$cachedExpandedStacks;

    /**
     * @author embeddedt, HoYin1600p
     * @reason Retain expanded ingredient arrays only while the VM has spare
     * memory, without overriding arrays explicitly installed by another mod.
     */
    @Overwrite
    public ItemStack[] getItems() {
        if (this.checkInvalidation()) {
            this.markValid();
        }

        ItemStack[] forced = this.itemStacks;
        if (forced != null) {
            return forced;
        }

        IngredientItemStacksSoftReference cache =
                this.vha$cachedExpandedStacks;
        if (cache != null) {
            ItemStack[] stacks = cache.get();
            if (stacks != null) {
                return stacks;
            }
        }

        IngredientItemStacksSoftReference.clearCollectedReferences();
        ItemStack[] stacks = this.vha$computeExpandedStacks();
        this.vha$cachedExpandedStacks =
                new IngredientItemStacksSoftReference(this, stacks);
        return stacks;
    }

    @Unique
    private ItemStack[] vha$computeExpandedStacks() {
        if (this.values.length == 1
                && this.values[0] instanceof Ingredient.TagValue
                && !IngredientReloadTracker.active()) {
            Optional<HolderSet.Named<Item>> tag = Registry.ITEM.getTag(
                    ((IngredientTagValueView) this.values[0]).vha$getTag()
            );
            if (tag.isPresent() && tag.get().size() > 0) {
                HolderSet.Named<Item> holders = tag.get();
                ItemStack[] stacks = new ItemStack[holders.size()];
                for (int index = 0; index < holders.size(); index++) {
                    stacks[index] = new ItemStack(holders.get(index));
                }
                return stacks;
            }
        }

        ArrayList<ItemStack> stacks = new ArrayList<>(2);
        for (Ingredient.Value value : this.values) {
            Collection<ItemStack> valueStacks = value.getItems();
            stacks.ensureCapacity(stacks.size() + valueStacks.size());
            stacks.addAll(valueStacks);
        }
        return stacks.toArray(ItemStack[]::new);
    }

    @Override
    public void vha$clearExpansionReference(
            IngredientItemStacksSoftReference expected
    ) {
        if (this.vha$cachedExpandedStacks == expected) {
            this.vha$cachedExpandedStacks = null;
        }
    }

    @Inject(method = "invalidate", at = @At("RETURN"), remap = false)
    private void vha$invalidateExpansionReference(CallbackInfo callback) {
        this.vha$cachedExpandedStacks = null;
    }
}
