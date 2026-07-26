package dev.hoyin1600p.launchfastertoo.mixin.compat.jei;

import dev.hoyin1600p.launchfastertoo.client.LaunchFasterTooClientConfig;
import dev.hoyin1600p.launchfastertoo.client.compat.jei.AsyncJeiCoordinator;
import mezz.jei.forge.config.ModIdFormattingConfig;
import mezz.jei.forge.events.RuntimeEventSubscriptions;
import mezz.jei.forge.startup.ClientLifecycleHandler;
import mezz.jei.startup.JeiStarter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientLifecycleHandler.class, remap = false)
public abstract class ClientLifecycleHandlerMixin {
    @Shadow
    @Final
    private JeiStarter jeiStarter;

    @Shadow
    @Final
    private RuntimeEventSubscriptions runtimeSubscriptions;

    @Shadow
    @Final
    private ModIdFormattingConfig modIdFormattingConfig;

    @Inject(method = "startJei", at = @At("HEAD"), cancellable = true)
    private void launchfastertoo$startGuardedJei(CallbackInfo ci) {
        if (!LaunchFasterTooClientConfig.VALUES.enableClientOptimizations.get()
                || !LaunchFasterTooClientConfig.VALUES.asyncJeiStartup.get()) {
            return;
        }
        ci.cancel();
        modIdFormattingConfig.checkForModNameFormatOverride();
        AsyncJeiCoordinator.start(jeiStarter, runtimeSubscriptions);
    }

    @Inject(method = "stopJei", at = @At("HEAD"), cancellable = true)
    private void launchfastertoo$stopGuardedJei(CallbackInfo ci) {
        if ((!LaunchFasterTooClientConfig.VALUES.enableClientOptimizations.get()
                || !LaunchFasterTooClientConfig.VALUES.asyncJeiStartup.get())
                && !AsyncJeiCoordinator.isManagingStartup()) {
            return;
        }
        ci.cancel();
        AsyncJeiCoordinator.stop(runtimeSubscriptions);
    }
}
