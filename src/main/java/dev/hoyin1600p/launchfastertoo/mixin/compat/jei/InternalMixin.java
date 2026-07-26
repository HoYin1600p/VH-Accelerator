package dev.hoyin1600p.launchfastertoo.mixin.compat.jei;

import dev.hoyin1600p.launchfastertoo.client.compat.jei.AsyncJeiCoordinator;
import java.util.Optional;
import mezz.jei.common.Internal;
import mezz.jei.common.ingredients.RegisteredIngredients;
import mezz.jei.common.runtime.JeiHelpers;
import mezz.jei.common.runtime.JeiRuntime;
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
        JeiHelpers helpers = AsyncJeiCoordinator.getThreadHelpers();
        if (helpers != null) {
            cir.setReturnValue(helpers);
        }
    }

    @Inject(method = "getRegisteredIngredients", at = @At("HEAD"), cancellable = true)
    private static void launchfastertoo$getThreadIngredients(
            CallbackInfoReturnable<RegisteredIngredients> cir
    ) {
        RegisteredIngredients ingredients =
                AsyncJeiCoordinator.getThreadRegisteredIngredients();
        if (ingredients != null) {
            cir.setReturnValue(ingredients);
        }
    }

    @Inject(method = "getRuntime", at = @At("HEAD"), cancellable = true)
    private static void launchfastertoo$getThreadRuntime(
            CallbackInfoReturnable<Optional<JeiRuntime>> cir
    ) {
        Optional<JeiRuntime> runtime = AsyncJeiCoordinator.getThreadRuntime();
        if (runtime != null) {
            cir.setReturnValue(runtime);
        }
    }
}
