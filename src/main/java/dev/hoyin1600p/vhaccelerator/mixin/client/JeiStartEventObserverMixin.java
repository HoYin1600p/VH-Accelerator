package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.compat.jei.JeiRecoveryReload;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.JeiRuntimeEpoch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures JEI's native restart operation without linking the universal jar to
 * either JEI generation at runtime.
 */
@Pseudo
@Mixin(
        targets = "mezz.jei.forge.startup.StartEventObserver",
        remap = false
)
public abstract class JeiStartEventObserverMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private static Runnable vhaccelerator$ownStartedRuntime(Runnable action) {
        return JeiRuntimeEpoch.wrapStart(action);
    }

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 1)
    private static Runnable vhaccelerator$invalidateStoppedRuntime(Runnable action) {
        return JeiRuntimeEpoch.wrapStop(action);
    }

    @Shadow
    private void restart() {
        throw new AssertionError("Mixin shadow was not transformed");
    }

    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void vhaccelerator$bindRecoveryReload(
            Runnable startRunnable,
            Runnable stopRunnable,
            CallbackInfo callback
    ) {
        JeiRecoveryReload.bind(this::restart);
    }
}
