package dev.hoyin1600p.launchfastertoo.mixin.compat.jei;

import dev.hoyin1600p.launchfastertoo.client.compat.jei.AsyncJeiCoordinator;
import mezz.jei.Internal;
import mezz.jei.ingredients.IngredientVisibility;
import mezz.jei.ingredients.RegisteredIngredients;
import mezz.jei.load.PluginLoader;
import mezz.jei.runtime.JeiHelpers;
import mezz.jei.util.RecipeErrorUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PluginLoader.class, remap = false)
public abstract class PluginLoaderMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/Internal;setRegisteredIngredients(Lmezz/jei/ingredients/RegisteredIngredients;)V"
            )
    )
    private void launchfastertoo$deferRegisteredIngredients(RegisteredIngredients ingredients) {
        AsyncJeiCoordinator.setRegisteredIngredients(ingredients);
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/util/RecipeErrorUtil;setRegisteredIngredients(Lmezz/jei/ingredients/RegisteredIngredients;)V"
            )
    )
    private void launchfastertoo$deferRecipeErrorIngredients(RegisteredIngredients ingredients) {
        AsyncJeiCoordinator.setRecipeErrorIngredients(ingredients);
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/Internal;setIngredientVisibility(Lmezz/jei/ingredients/IngredientVisibility;)V"
            )
    )
    private void launchfastertoo$deferIngredientVisibility(IngredientVisibility visibility) {
        AsyncJeiCoordinator.setIngredientVisibility(visibility);
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/Internal;setHelpers(Lmezz/jei/runtime/JeiHelpers;)V"
            )
    )
    private void launchfastertoo$deferHelpers(JeiHelpers helpers) {
        AsyncJeiCoordinator.setHelpers(helpers);
    }
}
