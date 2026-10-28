package dev.hoyin1600p.vhaccelerator.compat.lootjs;

import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import dev.latvian.mods.kubejs.item.ItemStackJS;
import dev.latvian.mods.kubejs.item.ingredient.IngredientJS;
import dev.latvian.mods.kubejs.item.ingredient.RegexIngredientJS;
import java.util.List;

/**
 * Answers LootJS's "does this filter ingredient match anything?" for an exact KubeJS regex ingredient.
 *
 * <p>LootJS builds every script filter with {@code IngredientJS.of(filter)} and asks {@code isEmpty()} so it can warn
 * about an invalid ingredient. For a regex that is {@code getFirst().isEmpty()}, and KubeJS's default
 * {@code getFirst()} runs {@code getStacks()} first: it tests every item of {@code ItemStackJS.getList()} with the
 * regex and copies each match into a set, only to look at the first one. A regex ingredient accepts only non-empty
 * stacks, and every stack in the list is non-empty, so the answer is simply "does any listed stack pass
 * {@code test}". This stops at the first one. The ingredient is later used through {@code testVanilla}, which this
 * does not touch, so the loot results are the same.</p>
 */
public final class LootJsRegexFilters {
    public static final String MIXIN = "dev.hoyin1600p.vhaccelerator.mixin.compat.lootjs.LootJsItemFilterMixin";

    private LootJsRegexFilters() { }

    /** Redirect target for {@code LootJSPlugin.ofItemFilter}; never throws anything the original would not. */
    public static boolean isEmpty(IngredientJS ingredient) {
        if (ingredient == null || ingredient.getClass() != RegexIngredientJS.class
                || !CompatMixinGroups.activationAllowed(MIXIN)) {
            return ingredient.isEmpty();
        }
        boolean empty;
        try {
            empty = noMatch(ingredient, ItemStackJS.getList());
        } catch (RuntimeException | LinkageError failure) {
            // The original check reproduces the same failure, or answers normally if only this walk failed.
            return ingredient.isEmpty();
        }
        CompatMixinGroups.recordOptimizationUse(MIXIN);
        return empty;
    }

    /** True when no stack passes the ingredient, stopping at the first that does. */
    static boolean noMatch(IngredientJS ingredient, List<ItemStackJS> stacks) {
        for (ItemStackJS stack : stacks) {
            if (ingredient.test(stack)) return false;
        }
        return true;
    }
}
