package dev.hoyin1600p.launchfastertoo.mixin.compat.jei;

import dev.hoyin1600p.launchfastertoo.client.compat.jei.AsyncJeiCoordinator;
import java.util.List;
import java.util.function.Consumer;
import mezz.jei.api.IModPlugin;
import mezz.jei.common.load.PluginCaller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PluginCaller.class, remap = false)
public abstract class PluginCallerMixin {
    @Inject(method = "callOnPlugins", at = @At("HEAD"), cancellable = true)
    private static void launchfastertoo$routePluginCallbacksToMain(
            String title,
            List<IModPlugin> plugins,
            Consumer<IModPlugin> callback,
            CallbackInfo ci
    ) {
        if (AsyncJeiCoordinator.routePluginCallToMain(title, plugins, callback)) {
            ci.cancel();
        }
    }
}
