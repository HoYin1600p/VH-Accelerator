/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/forge/recipe/IngredientValueDeduplicator.java
 * Upstream commit: b26ab375b56d9ec34bb1aa51a8b3cc2f78b2b939
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * Original inspiration credited upstream: Uncandango's AllTheLeaks
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; replaced the permanent strong interner with a weak,
 * reference-queue-cleaned Forge 40 interner that includes capability equality.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe;

import dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.recipe.dedup.IngredientItemValueAccess;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public final class IngredientValueDeduplicator {
    private static final Map<StackKey, ValueReference> VALUES =
            new HashMap<>();
    private static final ReferenceQueue<Ingredient.ItemValue> QUEUE =
            new ReferenceQueue<>();

    private IngredientValueDeduplicator() {
    }

    public static Ingredient.Value deduplicate(Ingredient.Value value) {
        if (value.getClass() != Ingredient.ItemValue.class) {
            return value;
        }

        Ingredient.ItemValue itemValue = (Ingredient.ItemValue) value;
        ItemStack stack = ((IngredientItemValueAccess) itemValue)
                .vha$getItem();
        StackKey key = new StackKey(stack);
        synchronized (VALUES) {
            drainQueue();
            ValueReference reference = VALUES.get(key);
            Ingredient.ItemValue existing =
                    reference == null ? null : reference.get();
            if (existing != null) {
                return existing;
            }
            VALUES.put(key, new ValueReference(key, itemValue));
            return itemValue;
        }
    }

    private static void drainQueue() {
        ValueReference reference;
        while ((reference = (ValueReference) QUEUE.poll()) != null) {
            VALUES.remove(reference.key, reference);
        }
    }

    private static final class ValueReference
            extends WeakReference<Ingredient.ItemValue> {
        private final StackKey key;

        private ValueReference(
                StackKey key,
                Ingredient.ItemValue value
        ) {
            super(value, QUEUE);
            this.key = key;
        }
    }

    private static final class StackKey {
        private final ItemStack stack;
        private final int hashCode;

        private StackKey(ItemStack source) {
            this.stack = source.copy();
            int hash = System.identityHashCode(this.stack.getItem());
            hash = 31 * hash + this.stack.getCount();
            hash = 31 * hash + (this.stack.getTag() == null
                    ? 0
                    : this.stack.getTag().hashCode());
            this.hashCode = hash;
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StackKey key)) {
                return false;
            }
            return this.stack.getCount() == key.stack.getCount()
                    && ItemStack.isSameItemSameTags(this.stack, key.stack);
        }
    }
}
