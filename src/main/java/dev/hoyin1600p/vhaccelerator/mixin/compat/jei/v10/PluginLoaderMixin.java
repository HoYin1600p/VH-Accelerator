package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v10;

import dev.hoyin1600p.vhaccelerator.client.compat.jei.v10.AsyncJeiCoordinator;
import mezz.jei.common.Internal;
import mezz.jei.common.ingredients.RegisteredIngredients;
import mezz.jei.common.load.PluginLoader;
import mezz.jei.common.runtime.JeiHelpers;
import mezz.jei.common.util.RecipeErrorUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(value = PluginLoader.class, remap = false)
public abstract class PluginLoaderMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/common/Internal;setRegisteredIngredients(Lmezz/jei/common/ingredients/RegisteredIngredients;)V"
            )
    )
    private void vhaccelerator$deferRegisteredIngredients(RegisteredIngredients ingredients) {
        AsyncJeiCoordinator.setRegisteredIngredients(ingredients);
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/common/util/RecipeErrorUtil;setRegisteredIngredients(Lmezz/jei/common/ingredients/RegisteredIngredients;)V"
            )
    )
    private void vhaccelerator$deferRecipeErrorIngredients(RegisteredIngredients ingredients) {
        AsyncJeiCoordinator.setRecipeErrorIngredients(ingredients);
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/common/Internal;setHelpers(Lmezz/jei/common/runtime/JeiHelpers;)V"
            )
    )
    private void vhaccelerator$deferHelpers(JeiHelpers helpers) {
        AsyncJeiCoordinator.setHelpers(helpers);
    }
}
