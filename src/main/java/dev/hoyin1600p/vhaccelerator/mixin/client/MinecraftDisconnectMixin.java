package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.DisconnectTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftDisconnectMixin {
    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void vhaccelerator$beginClientTeardown(
            Screen progressScreen,
            CallbackInfo callback
    ) {
        DisconnectTimer.beginClientTeardown();
    }

    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("RETURN"))
    private void vhaccelerator$finishClientTeardown(
            Screen progressScreen,
            CallbackInfo callback
    ) {
        DisconnectTimer.finishClientTeardown();
    }
}
