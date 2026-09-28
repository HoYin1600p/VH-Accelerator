package dev.hoyin1600p.vhaccelerator.mixin.compat.buildscape;

import dev.hoyin1600p.vhaccelerator.client.compat.buildscape.BuildScapeModelOwnership;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BuildScape skips its own parallel model parse and bake when this reports
 * that another mod handles them. VHA says so only while it owns that stage;
 * otherwise BuildScape's own answer stands.
 */
@Pseudo
@Mixin(targets = "com.kingodogo.buildscape.client.performance.LaunchFasterInterop", remap = false)
public abstract class LaunchFasterInteropMixin {
    @Inject(method = "isParallelModelLoadingEnabled", at = @At("HEAD"), cancellable = true, require = 0)
    private static void vhaccelerator$vhaLoadsModels(CallbackInfoReturnable<Boolean> callback) {
        if (BuildScapeModelOwnership.vhaLoads()) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "isParallelModelBakingEnabled", at = @At("HEAD"), cancellable = true, require = 0)
    private static void vhaccelerator$vhaBakesModels(CallbackInfoReturnable<Boolean> callback) {
        if (BuildScapeModelOwnership.vhaBakes()) {
            callback.setReturnValue(true);
        }
    }
}
