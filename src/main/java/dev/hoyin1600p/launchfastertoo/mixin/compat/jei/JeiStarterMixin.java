package dev.hoyin1600p.launchfastertoo.mixin.compat.jei;

import dev.hoyin1600p.launchfastertoo.client.compat.jei.AsyncJeiCoordinator;
import java.util.List;
import java.util.function.Consumer;
import mezz.jei.api.IModPlugin;
import mezz.jei.Internal;
import mezz.jei.load.PluginCaller;
import mezz.jei.runtime.JeiRuntime;
import mezz.jei.startup.JeiStarter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = JeiStarter.class, remap = false)
public abstract class JeiStarterMixin {
    @Redirect(
            method = "start",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/Internal;setRuntime(Lmezz/jei/runtime/JeiRuntime;)V"
            )
    )
    private void launchfastertoo$deferRuntime(JeiRuntime runtime) {
        AsyncJeiCoordinator.setRuntime(runtime);
    }

    @Redirect(
            method = "start",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/load/PluginCaller;callOnPlugins(Ljava/lang/String;Ljava/util/List;Ljava/util/function/Consumer;)V"
            )
    )
    private void launchfastertoo$deferRuntimePlugins(
            String title,
            List<IModPlugin> plugins,
            Consumer<IModPlugin> callback
    ) {
        AsyncJeiCoordinator.callRuntimePlugins(title, plugins, callback);
    }
}
