package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Mirrors JEI's structural recipe validation without expanding ordinary
 * vanilla ingredients. Ingredient#getItems always returns a non-null array
 * for the base class; custom Forge ingredient subclasses retain JEI's
 * original expansion check.
 */
public final class VanillaRecipeValidation {
    private VanillaRecipeValidation() {
    }

    public static boolean isValid(Recipe<?> recipe, int maxInputs) {
        if (recipe.isSpecial()) {
            return true;
        }

        ItemStack output = recipe.getResultItem();
        if (output == null || output.isEmpty()) {
            return false;
        }
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients == null) {
            return false;
        }

        int inputCount = 0;
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null) {
                return false;
            }
            if (ingredient.getClass() != Ingredient.class
                    && ingredient.getItems() == null) {
                return false;
            }
            inputCount++;
        }
        if (inputCount > maxInputs) {
            return false;
        }
        return inputCount != 0 || maxInputs <= 0;
    }
}
