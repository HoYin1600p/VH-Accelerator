package dev.hoyin1600p.vhaccelerator.mixin;

import dev.hoyin1600p.vhaccelerator.startup.ServerLaunchTimer;
import dev.hoyin1600p.vhaccelerator.startup.StartupRecorder;
import dev.hoyin1600p.vhaccelerator.startup.ServerStartupReport;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forge 40.3.11 logs Done inside initServer, before either starting/started event. */
@Mixin(DedicatedServer.class)
public abstract class DedicatedServerStartupMixin {
    @Unique private ServerStartupReport.Span vhaccelerator$initialization;

    @Inject(method = "initServer", at = @At("HEAD"))
    private void vhaccelerator$initializationStart(CallbackInfoReturnable<Boolean> callback) {
        ServerLaunchTimer.checkpoint("dedicated_initialization", "hook:DedicatedServer.initServer HEAD");
        StartupRecorder.beforeServerInit();
        vhaccelerator$initialization = ServerLaunchTimer.beginSpan("dedicated.initialization", null, null, null);
    }

    @Inject(method = "initServer", at = @At(value = "CONSTANT",
            args = "stringValue=Done ({})! For help, type \"help\""))
    private void vhaccelerator$done(CallbackInfoReturnable<Boolean> callback) {
        ServerLaunchTimer.markDone();
    }

    @Inject(method = "initServer", at = @At("RETURN"))
    private void vhaccelerator$initializationEnd(CallbackInfoReturnable<Boolean> callback) {
        ServerLaunchTimer.endSpan(vhaccelerator$initialization,
                callback.getReturnValue() ? null : new IllegalStateException("initialization rejected"));
        vhaccelerator$initialization = null;
    }
}
