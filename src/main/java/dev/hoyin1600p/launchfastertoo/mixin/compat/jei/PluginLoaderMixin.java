package dev.hoyin1600p.launchfastertoo.mixin.compat.jei;

import dev.hoyin1600p.launchfastertoo.client.compat.jei.AsyncJeiCoordinator;
import mezz.jei.common.Internal;
import mezz.jei.common.ingredients.RegisteredIngredients;
import mezz.jei.common.load.PluginLoader;
import mezz.jei.common.runtime.JeiHelpers;
import mezz.jei.common.util.RecipeErrorUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PluginLoader.class, remap = false)
public abstract class PluginLoaderMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/common/Internal;setRegisteredIngredients(Lmezz/jei/common/ingredients/RegisteredIngredients;)V"
            )
    )
    private void launchfastertoo$deferRegisteredIngredients(RegisteredIngredients ingredients) {
        AsyncJeiCoordinator.setRegisteredIngredients(ingredients);
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/common/util/RecipeErrorUtil;setRegisteredIngredients(Lmezz/jei/common/ingredients/RegisteredIngredients;)V"
            )
    )
    private void launchfastertoo$deferRecipeErrorIngredients(RegisteredIngredients ingredients) {
        AsyncJeiCoordinator.setRecipeErrorIngredients(ingredients);
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/common/Internal;setHelpers(Lmezz/jei/common/runtime/JeiHelpers;)V"
            )
    )
    private void launchfastertoo$deferHelpers(JeiHelpers helpers) {
        AsyncJeiCoordinator.setHelpers(helpers);
    }
}
