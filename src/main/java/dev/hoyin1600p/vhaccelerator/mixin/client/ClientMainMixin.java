package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.AsyncCrashReportPreload;
import dev.hoyin1600p.vhaccelerator.client.profiling.LaunchTimer;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public abstract class ClientMainMixin {
    @Inject(method = "main", at = @At("HEAD"))
    private static void vhaccelerator$startClientTimer(String[] arguments, CallbackInfo callback) {
        LaunchTimer.markStart();
    }

    @Redirect(
            method = "main",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/CrashReport;preload()V"
            )
    )
    private static void vhaccelerator$preloadCrashReportInBackground() {
        AsyncCrashReportPreload.preload();
    }

    @Inject(
            method = "main",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/Bootstrap;validate()V",
                    shift = At.Shift.AFTER
            )
    )
    private static void vhaccelerator$startCrashReportPreload(String[] arguments, CallbackInfo callback) {
        AsyncCrashReportPreload.startAfterBootstrap();
    }
}

