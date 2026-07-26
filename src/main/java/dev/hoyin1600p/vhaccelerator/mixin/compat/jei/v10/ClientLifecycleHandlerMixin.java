package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v10;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.v10.AsyncJeiCoordinator;
import mezz.jei.common.startup.JeiStarter;
import mezz.jei.forge.events.RuntimeEventSubscriptions;
import mezz.jei.forge.startup.ClientLifecycleHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = ClientLifecycleHandler.class, remap = false)
public abstract class ClientLifecycleHandlerMixin {
    @Shadow
    @Final
    private JeiStarter jeiStarter;

    @Shadow
    @Final
    private RuntimeEventSubscriptions runtimeSubscriptions;

    @Inject(method = "startJei", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$startGuardedJei(CallbackInfo ci) {
        if (!VHAcceleratorClientConfig.VALUES.enableClientOptimizations.get()
                || !VHAcceleratorClientConfig.VALUES.asyncJeiStartup.get()) {
            return;
        }
        ci.cancel();
        AsyncJeiCoordinator.start(jeiStarter, runtimeSubscriptions);
    }

    @Inject(method = "stopJei", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$stopGuardedJei(CallbackInfo ci) {
        if ((!VHAcceleratorClientConfig.VALUES.enableClientOptimizations.get()
                || !VHAcceleratorClientConfig.VALUES.asyncJeiStartup.get())
                && !AsyncJeiCoordinator.isManagingStartup()) {
            return;
        }
        ci.cancel();
        AsyncJeiCoordinator.stop(runtimeSubscriptions);
    }
}
