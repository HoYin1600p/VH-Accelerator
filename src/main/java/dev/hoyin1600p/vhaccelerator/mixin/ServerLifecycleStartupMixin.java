package dev.hoyin1600p.vhaccelerator.mixin;

import dev.hoyin1600p.vhaccelerator.startup.ServerLaunchTimer;
import dev.hoyin1600p.vhaccelerator.startup.StartupRecorder;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Timestamp emission before unrelated event listeners can add latency. */
@Mixin(value = ServerLifecycleHooks.class, remap = false)
public abstract class ServerLifecycleStartupMixin {
    @Inject(method = "handleServerStarted", at = @At("HEAD"))
    private static void vhaccelerator$started(CallbackInfo callback) {
        ServerLaunchTimer.markStartedEmission();
    }

    /**
     * The ledger is complete once every started-event listener has returned, or the post has thrown;
     * a failing listener is exactly when the report matters, so this wraps the post instead of RETURN.
     */
    @Redirect(method = "handleServerStarted", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean vhaccelerator$post(IEventBus bus, Event event) {
        try {
            return bus.post(event);
        } finally {
            StartupRecorder.startedReturned();
        }
    }

    @Inject(method = "handleServerStarting", at = @At("HEAD"))
    private static void vhaccelerator$starting(CallbackInfoReturnable<Boolean> callback) {
        ServerLaunchTimer.checkpoint("server_starting", "hook:ServerLifecycleHooks.handleServerStarting HEAD");
    }
}
