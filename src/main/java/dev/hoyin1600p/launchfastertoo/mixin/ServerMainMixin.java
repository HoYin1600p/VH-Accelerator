package dev.hoyin1600p.launchfastertoo.mixin;

import dev.hoyin1600p.launchfastertoo.ServerLaunchTimer;
import net.minecraft.server.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public abstract class ServerMainMixin {
    @Inject(method = "main", at = @At("HEAD"))
    private static void launchfastertoo$startServerTimer(String[] arguments, CallbackInfo callback) {
        ServerLaunchTimer.markStart();
    }
}

