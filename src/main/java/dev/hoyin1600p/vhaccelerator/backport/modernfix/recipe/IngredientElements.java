/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_ingredients/ForgeHooksMixin.java
 *                  src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_ingredients/IngredientMixin.java
 * Upstream commit: eed320b05588bcf3df39646701221242c5b5d9bc (introduction)
 *                  b26ab375b56d9ec34bb1aa51a8b3cc2f78b2b939 (current location)
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; retargeted to Forge 40.3.11 with an exact stack count,
 * the existing reload guard, and a fall-through for expanded, custom, or
 * mod-valued ingredients.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeConfig;

/**
 * Answers {@code ForgeHooks#hasNoElements} for vanilla ingredients without
 * expanding them into {@code ItemStack} arrays.
 *
 * <p>{@code Recipe#isIncomplete} calls {@code hasNoElements} for every
 * ingredient of every recipe when {@code ClientRecipeBook#setupCollections}
 * runs on each join, and Forge 40 implements it by calling
 * {@code Ingredient#getItems()}, which allocates a stack per tag entry and a
 * distinct-filtered array per ingredient. This helper reproduces the exact
 * result of that implementation:
 *
 * <ul>
 * <li>{@code items.length == 0} becomes a zero tally;</li>
 * <li>{@code items.length == 1 && !treatEmptyTagsAsAir} becomes a tally of
 * one, answered {@code true} for the synthetic empty-tag barrier stack, and
 * for an {@code ItemValue} stack by applying Forge's own barrier predicate to
 * that object (mods do wrap expanded barrier stacks back into
 * ingredients);</li>
 * <li>anything else is {@code false}.</li>
 * </ul>
 *
 * <p>The shortcut yields to Forge whenever the result could differ: custom
 * {@code Ingredient} subclasses (they may override {@code getItems}),
 * unknown {@code Value} implementations, an already expanded or mod-forced
 * {@code itemStacks} array (Forge's check is then already cheap), and the
 * server data reload window in which CraftTweaker and KubeJS patch
 * {@code TagValue#getItems} with their own tag context.
 */
public final class IngredientElements {
    private IngredientElements() {
    }

    /**
     * @return Forge's answer, or null when the caller must run the original
     * implementation
     */
    @Nullable
    public static Boolean hasNoElements(Ingredient ingredient) {
        if (!ingredient.isVanilla()
                || IngredientReloadTracker.active()
                || !(ingredient instanceof IngredientElementsView view)
                || view.vha$getItemStacks() != null) {
            return null;
        }
        IngredientElementTally tally = new IngredientElementTally();
        for (Ingredient.Value value : view.vha$getValues()) {
            // Exact classes only: a subclass may override getItems().
            if (value.getClass() == Ingredient.ItemValue.class
                    && value instanceof IngredientItemValueStackView itemValue) {
                tally.addItemValueStack(itemValue.vha$getStack());
            } else if (value.getClass() == Ingredient.TagValue.class
                    && value instanceof IngredientTagValueView tagValue) {
                TagKey<Item> key = tagValue.vha$getTag();
                Optional<HolderSet.Named<Item>> tag = Registry.ITEM.getTag(key);
                int size = tag.isPresent() ? tag.get().size() : 0;
                if (size > 0) {
                    tally.addTag(size, false);
                } else {
                    // Same read TagValue#getItems performs for an empty tag.
                    tally.addTag(0, treatEmptyTagsAsAir());
                }
            } else {
                return null;
            }
        }
        int total = tally.total();
        if (total == 0) {
            return true;
        }
        if (total == 1 && !treatEmptyTagsAsAir()) {
            if (tally.singleStackIsEmptyTagBarrier()) {
                return true;
            }
            return tally.singleItemValueStack() instanceof ItemStack stack
                    && isEmptyTagBarrier(stack);
        }
        return false;
    }

    /** Forge 40's {@code ForgeHooks#hasNoElements} single-stack predicate, verbatim. */
    static boolean isEmptyTagBarrier(ItemStack stack) {
        return stack.getItem() == Items.BARRIER
                && stack.getHoverName() instanceof TextComponent hoverName
                && hoverName.getText().startsWith("Empty Tag: ");
    }

    private static boolean treatEmptyTagsAsAir() {
        return ForgeConfig.SERVER.treatEmptyTagsAsAir.get();
    }
}
