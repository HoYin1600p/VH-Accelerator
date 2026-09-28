package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.IntegratedServerJoinGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Consumes the join marker before Forge's channel dispatch; the release runs
 * as a client-thread task, after every packet the server sent before it.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerJoinGateMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$joinMarker(ClientboundCustomPayloadPacket packet, CallbackInfo callback) {
        if (IntegratedServerJoinGate.MARKER.equals(packet.getIdentifier())) {
            IntegratedServerJoinGate.onMarker(minecraft);
            callback.cancel();
        }
    }
}
