package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v9;

import dev.hoyin1600p.vhaccelerator.client.compat.jei.v9.RecipeMapIndexAccess;
import java.util.List;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.core.collect.SetMultiMap;
import mezz.jei.recipes.RecipeIngredientTable;
import mezz.jei.recipes.RecipeMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(value = RecipeMap.class, remap = false)
public abstract class RecipeMapIndexMixin
        implements RecipeMapIndexAccess {
    @Shadow
    @Final
    private RecipeIngredientTable recipeTable;

    @Shadow
    @Final
    private SetMultiMap<String, RecipeType<?>>
            ingredientUidToCategoryMap;

    @Override
    public <T> void vhaccelerator$addIndexedRecipe(
            RecipeType<T> recipeType,
            T recipe,
            List<List<String>> uidGroups
    ) {
        for (List<String> group : uidGroups) {
            if (group.isEmpty()) {
                continue;
            }
            for (String uid : group) {
                ingredientUidToCategoryMap.put(uid, recipeType);
            }
            recipeTable.add(recipe, recipeType, group);
        }
    }
}
