package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.PacketDecodeProfiler;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConnectionProtocol.class)
public abstract class ConnectionProtocolMixin {
    @Inject(method = "createPacket", at = @At("HEAD"))
    private void vhaccelerator$beginLargePacketDecode(
            PacketFlow direction,
            int packetId,
            FriendlyByteBuf buffer,
            CallbackInfoReturnable<Packet<?>> callback
    ) {
        PacketDecodeProfiler.begin(packetId, buffer);
    }

    @Inject(method = "createPacket", at = @At("RETURN"))
    private void vhaccelerator$finishLargePacketDecode(
            PacketFlow direction,
            int packetId,
            FriendlyByteBuf buffer,
            CallbackInfoReturnable<Packet<?>> callback
    ) {
        PacketDecodeProfiler.finish(callback.getReturnValue());
    }
}
