package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v10;

import dev.hoyin1600p.vhaccelerator.client.compat.jei.v10.AsyncJeiCoordinator;
import java.util.List;
import java.util.function.Consumer;
import mezz.jei.api.IModPlugin;
import mezz.jei.common.Internal;
import mezz.jei.common.load.PluginCaller;
import mezz.jei.common.runtime.JeiRuntime;
import mezz.jei.common.startup.JeiStarter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(value = JeiStarter.class, remap = false)
public abstract class JeiStarterMixin {
    @Redirect(
            method = "start",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/common/Internal;setRuntime(Lmezz/jei/common/runtime/JeiRuntime;)V"
            )
    )
    private void vhaccelerator$deferRuntime(JeiRuntime runtime) {
        AsyncJeiCoordinator.setRuntime(runtime);
    }

    @Redirect(
            method = "start",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/common/load/PluginCaller;callOnPlugins(Ljava/lang/String;Ljava/util/List;Ljava/util/function/Consumer;)V"
            )
    )
    private void vhaccelerator$deferRuntimePlugins(
            String title,
            List<IModPlugin> plugins,
            Consumer<IModPlugin> callback
    ) {
        AsyncJeiCoordinator.callRuntimePlugins(title, plugins, callback);
    }
}
