package dev.hoyin1600p.launchfastertoo.mixin.compat.jei;

import dev.hoyin1600p.launchfastertoo.client.compat.jei.AsyncJeiCoordinator;
import mezz.jei.Internal;
import mezz.jei.ingredients.IngredientVisibility;
import mezz.jei.ingredients.RegisteredIngredients;
import mezz.jei.runtime.JeiHelpers;
import mezz.jei.runtime.JeiRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Internal.class, remap = false)
public abstract class InternalMixin {
    @Inject(method = "getHelpers", at = @At("HEAD"), cancellable = true)
    private static void launchfastertoo$getThreadHelpers(
            CallbackInfoReturnable<JeiHelpers> cir
    ) {
        if (AsyncJeiCoordinator.isPreparingOnCurrentThread()) {
            cir.setReturnValue(AsyncJeiCoordinator.getThreadHelpers());
        }
    }

    @Inject(method = "getRegisteredIngredients", at = @At("HEAD"), cancellable = true)
    private static void launchfastertoo$getThreadIngredients(
            CallbackInfoReturnable<RegisteredIngredients> cir
    ) {
        if (AsyncJeiCoordinator.isPreparingOnCurrentThread()) {
            cir.setReturnValue(AsyncJeiCoordinator.getThreadRegisteredIngredients());
        }
    }

    @Inject(method = "getRuntime", at = @At("HEAD"), cancellable = true)
    private static void launchfastertoo$getThreadRuntime(
            CallbackInfoReturnable<JeiRuntime> cir
    ) {
        if (AsyncJeiCoordinator.isPreparingOnCurrentThread()) {
            cir.setReturnValue(AsyncJeiCoordinator.getThreadRuntime());
        }
    }

    @Inject(method = "getIngredientVisibility", at = @At("HEAD"), cancellable = true)
    private static void launchfastertoo$getThreadVisibility(
            CallbackInfoReturnable<IngredientVisibility> cir
    ) {
        if (AsyncJeiCoordinator.isPreparingOnCurrentThread()) {
            cir.setReturnValue(AsyncJeiCoordinator.getThreadIngredientVisibility());
        }
    }
}
