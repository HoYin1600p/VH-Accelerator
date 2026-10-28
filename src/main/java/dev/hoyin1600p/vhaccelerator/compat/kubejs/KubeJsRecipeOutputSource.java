package dev.hoyin1600p.vhaccelerator.compat.kubejs;

import dev.latvian.mods.kubejs.item.ItemStackJS;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.filter.OutputFilter;
import dev.latvian.mods.kubejs.recipe.filter.RecipeFilter;
import dev.latvian.mods.kubejs.recipe.minecraft.CustomRecipeJS;
import dev.latvian.mods.kubejs.server.ServerSettings;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Output item table for {@link KubeJsRecipeIndex}.
 *
 * <p>{@code OutputFilter.test} is {@code recipe.hasOutput(ingredient, exact)}: true when some entry
 * of {@code outputItems} matches, or, for a {@code CustomRecipeJS} that still has its original
 * recipe and while {@code useOriginalRecipeForFilters} is set, when the original recipe's
 * non-empty result matches. When the ingredient is one plain {@code ItemStackJS}, both the exact and
 * the loose test require the same item, so the table keys recipes by every item they can output
 * and the filter's item picks a superset. NBT, count and chance are left to the real filter. A
 * result that throws is left unclassified (always a candidate) so the real filter sees it as
 * before. The registry-bound classes here are loaded only in the game.</p>
 */
final class KubeJsRecipeOutputSource implements KubeJsRecipeIndex.Source<Item> {
    private static final Field FILTER_OUT = filterOut();
    private ItemStackJS[] stacks = new ItemStackJS[0];
    private int[] starts = new int[] {0};
    private Recipe<?>[] originals = new Recipe<?>[0];
    private boolean useOriginal;

    private static Field filterOut() {
        try {
            Field field = OutputFilter.class.getDeclaredField("out");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    @Override
    public String name() {
        return "output item";
    }

    @Override
    public Item keyOf(RecipeFilter filter) {
        if (FILTER_OUT == null || filter.getClass() != OutputFilter.class) {
            return null;
        }
        try {
            Object ingredient = FILTER_OUT.get(filter);
            // Exactly ItemStackJS: not the empty stack and not the fluid stand-in subclass.
            if (ingredient != null && ingredient.getClass() == ItemStackJS.class) {
                return ((ItemStackJS) ingredient).getItem();
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            // fall through: not indexable
        }
        return null;
    }

    @Override
    public void snapshot(List<RecipeJS> recipes, KubeJsRecipeIndex.Sink<Item> sink) {
        int count = recipes.size();
        boolean original = ServerSettings.instance.useOriginalRecipeForFilters;
        int[] start = new int[count + 1];
        Recipe<?>[] originalRecipes = new Recipe<?>[count];
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += recipes.get(i).outputItems.size();
        }
        ItemStackJS[] flat = new ItemStackJS[total];
        int at = 0;
        for (int i = 0; i < count; i++) {
            RecipeJS recipe = recipes.get(i);
            start[i] = at;
            for (ItemStackJS stack : recipe.outputItems) {
                flat[at++] = stack;
                sink.put(i, stack.getItem());
            }
            Recipe<?> originalRecipe = recipe.originalRecipe;
            originalRecipes[i] = originalRecipe;
            if (original && originalRecipe != null && recipe instanceof CustomRecipeJS) {
                try {
                    ItemStack result = originalRecipe.getResultItem();
                    if (!result.isEmpty()) {
                        sink.put(i, result.getItem());
                    }
                } catch (Exception failure) {
                    sink.unknown(i);
                }
            }
        }
        start[count] = at;
        stacks = flat;
        starts = start;
        originals = originalRecipes;
        useOriginal = original;
    }

    @Override
    public boolean isCurrent(List<RecipeJS> recipes) {
        int[] start = starts;
        int count = start.length - 1;
        if (recipes.size() != count || ServerSettings.instance.useOriginalRecipeForFilters != useOriginal) {
            return false;
        }
        ItemStackJS[] known = stacks;
        Recipe<?>[] knownOriginals = originals;
        return KubeJsRecipeIndex.allMatch(count, i -> {
            RecipeJS recipe = recipes.get(i);
            if (recipe.originalRecipe != knownOriginals[i]) {
                return false;
            }
            List<ItemStackJS> outputs = recipe.outputItems;
            int from = start[i];
            int size = start[i + 1] - from;
            if (outputs.size() != size) {
                return false;
            }
            for (int j = 0; j < size; j++) {
                if (outputs.get(j) != known[from + j]) {
                    return false;
                }
            }
            return true;
        });
    }
}
