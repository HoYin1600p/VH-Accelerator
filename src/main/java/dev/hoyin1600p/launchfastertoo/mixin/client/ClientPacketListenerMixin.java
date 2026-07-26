package dev.hoyin1600p.launchfastertoo.mixin.client;

import dev.hoyin1600p.launchfastertoo.client.ServerLoginTimer;
import dev.hoyin1600p.launchfastertoo.client.ServerTransferTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Starts transfer timing when Minecraft begins handling the packet on its
 * main thread, immediately before the client world is replaced. Ignoring the
 * earlier network-thread dispatch prevents an old-world frame from completing
 * the measurement before the scheduled packet handler runs.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void launchfastertoo$startTransferTimer(
            ClientboundRespawnPacket packet,
            CallbackInfo callback
    ) {
        if (Minecraft.getInstance().isSameThread()
                && !ServerLoginTimer.isActive()) {
            ServerTransferTimer.markStart("respawn packet");
        }
    }
}
