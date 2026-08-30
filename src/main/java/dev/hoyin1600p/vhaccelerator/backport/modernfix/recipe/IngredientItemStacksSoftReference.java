/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/forge/recipe/IngredientItemStacksSoftReference.java
 * Upstream commit: 0f946343610227ae187ebd6f5a6cc3f293aa2592
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; added identity-safe owner cleanup for replaced cache references.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import net.minecraft.world.item.ItemStack;

public final class IngredientItemStacksSoftReference
        extends SoftReference<ItemStack[]> {
    private static final ReferenceQueue<ItemStack[]> QUEUE =
            new ReferenceQueue<>();

    private final IngredientExpansionCacheOwner owner;

    public IngredientItemStacksSoftReference(
            IngredientExpansionCacheOwner owner,
            ItemStack[] stacks
    ) {
        super(stacks, QUEUE);
        this.owner = owner;
    }

    public static void clearCollectedReferences() {
        Reference<? extends ItemStack[]> reference;
        while ((reference = QUEUE.poll()) != null) {
            IngredientItemStacksSoftReference ingredientReference =
                    (IngredientItemStacksSoftReference) reference;
            ingredientReference.owner.vha$clearExpansionReference(
                    ingredientReference
            );
        }
    }
}
