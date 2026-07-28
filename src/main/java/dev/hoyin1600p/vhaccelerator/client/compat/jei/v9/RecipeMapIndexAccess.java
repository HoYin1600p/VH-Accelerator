package dev.hoyin1600p.vhaccelerator.client.compat.jei.v9;

import java.util.List;
import mezz.jei.api.recipe.RecipeType;

public interface RecipeMapIndexAccess {
    <T> void vhaccelerator$addIndexedRecipe(
            RecipeType<T> recipeType,
            T recipe,
            List<List<String>> uidGroups
    );
}
