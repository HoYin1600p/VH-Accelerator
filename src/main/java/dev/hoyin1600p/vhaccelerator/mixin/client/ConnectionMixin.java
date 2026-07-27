package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.ClientWorkSession;
import dev.hoyin1600p.vhaccelerator.client.DisconnectTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Starts lifecycle invalidation before vanilla blocks the render thread on
 * Netty's channel-close future.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Unique
    private boolean vhaccelerator$timingClientDisconnect;

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void vhaccelerator$beginClientDisconnect(
            Component reason,
            CallbackInfo callback
    ) {
        if (!vhaccelerator$isActiveClientConnection()) {
            return;
        }
        vhaccelerator$timingClientDisconnect = true;
        DisconnectTimer.beginNetworkClose();
        ClientWorkSession.invalidate("network disconnect");
    }

    @Inject(method = "disconnect", at = @At("RETURN"))
    private void vhaccelerator$finishClientDisconnect(
            Component reason,
            CallbackInfo callback
    ) {
        if (vhaccelerator$timingClientDisconnect) {
            vhaccelerator$timingClientDisconnect = false;
            DisconnectTimer.finishNetworkClose();
        }
    }

    private boolean vhaccelerator$isActiveClientConnection() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.isSameThread()
                && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == (Object) this;
    }
}
