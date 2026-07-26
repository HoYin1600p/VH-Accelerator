package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.LaunchTimer;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public abstract class ClientMainMixin {
    @Inject(method = "main", at = @At("HEAD"))
    private static void vhaccelerator$startClientTimer(String[] arguments, CallbackInfo callback) {
        LaunchTimer.markStart();
    }
}

