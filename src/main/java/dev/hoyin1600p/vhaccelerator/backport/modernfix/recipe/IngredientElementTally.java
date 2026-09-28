/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_ingredients/IngredientMixin.java
 * Upstream commit: eed320b05588bcf3df39646701221242c5b5d9bc
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; replaced upstream's first-match containsItems with an
 * exact count of the stacks Forge 40's Ingredient#dissolve would produce, so
 * the "Empty Tag" barrier rule of ForgeHooks#hasNoElements is preserved.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Counts, without allocating any {@code ItemStack}, how many distinct stacks
 * Forge 40's {@code Ingredient#dissolve} would place in {@code itemStacks}.
 *
 * <p>{@code dissolve} concatenates {@code Value#getItems()} and applies
 * {@code Stream#distinct}, which for {@code ItemStack} (no {@code equals}
 * override) means identity. An {@code ItemValue} contributes the one stack it
 * holds, so two values holding the same stack object collapse into one; a
 * {@code TagValue} creates a fresh stack per tag entry, or one fresh
 * "Empty Tag" barrier stack when the tag is empty and
 * {@code treatEmptyTagsAsAir} is off, so those never collapse with anything.
 * {@code ForgeHooks#hasNoElements} then answers {@code true} for zero stacks,
 * and for exactly one stack only when that stack is the barrier.
 */
public final class IngredientElementTally {
    private final List<Object> itemValueStacks = new ArrayList<>(2);
    private int freshTagStacks;
    private int emptyTagBarriers;

    /** Records the stack an {@code ItemValue} holds; identical objects count once. */
    public void addItemValueStack(Object stack) {
        for (Object existing : itemValueStacks) {
            if (existing == stack) {
                return;
            }
        }
        itemValueStacks.add(stack);
    }

    /**
     * Records a bound tag: {@code size} fresh stacks, or one barrier stack
     * for an empty tag unless empty tags are treated as air.
     */
    public void addTag(int size, boolean treatEmptyTagsAsAir) {
        if (size > 0) {
            freshTagStacks += size;
        } else if (!treatEmptyTagsAsAir) {
            emptyTagBarriers++;
        }
    }

    /** Number of distinct stacks {@code dissolve} would produce. */
    public int total() {
        return itemValueStacks.size() + freshTagStacks + emptyTagBarriers;
    }

    /** Whether the single counted stack is Forge's synthetic empty-tag barrier. */
    public boolean singleStackIsEmptyTagBarrier() {
        return total() == 1 && emptyTagBarriers == 1;
    }

    /**
     * The single counted stack when it came from an {@code ItemValue}, so the
     * caller can apply Forge's barrier check to that exact object; otherwise
     * null.
     */
    @Nullable
    public Object singleItemValueStack() {
        return total() == 1 && itemValueStacks.size() == 1 ? itemValueStacks.get(0) : null;
    }
}
