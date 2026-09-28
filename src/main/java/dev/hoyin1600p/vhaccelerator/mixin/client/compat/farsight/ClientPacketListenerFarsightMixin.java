package dev.hoyin1600p.vhaccelerator.mixin.client.compat.farsight;

import dev.hoyin1600p.vhaccelerator.client.compat.farsight.FarsightChunkBound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds {@link FarsightChunkBound} from the vanilla packet handlers.
 *
 * <p>Only applied when Farsight ({@code farsight_view}) is installed and Vault
 * Render Optimization is not. The two HEAD hooks read the radius straight
 * from the packet: Farsight's {@code @Redirect}s only rewrite the
 * {@code chunkRadius()} / {@code getRadius()} invocations inside the handler
 * bodies, so the value seen here is what the server sent. The RETURN hook on
 * {@code handleLevelChunkWithLight} runs after the chunk has been stored and
 * its light queued, matching the point Embeddium uses for its own tracker.
 *
 * <p>HEAD hooks run before {@code PacketUtils.ensureRunningOnSameThread}, so
 * they check the thread themselves; the RETURN hook is only reached on the
 * client thread.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerFarsightMixin {
    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void vhaccelerator$recordLoginChunkRadius(
            ClientboundLoginPacket packet,
            CallbackInfo callback
    ) {
        if (Minecraft.getInstance().isSameThread()) {
            FarsightChunkBound.recordServerChunkRadius(packet.chunkRadius());
        }
    }

    @Inject(method = "handleSetChunkCacheRadius", at = @At("HEAD"))
    private void vhaccelerator$recordServerChunkRadius(
            ClientboundSetChunkCacheRadiusPacket packet,
            CallbackInfo callback
    ) {
        if (Minecraft.getInstance().isSameThread()) {
            FarsightChunkBound.recordServerChunkRadius(packet.getRadius());
        }
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void vhaccelerator$trackLoadedChunk(
            ClientboundLevelChunkWithLightPacket packet,
            CallbackInfo callback
    ) {
        FarsightChunkBound.onChunkLoaded(
                ((ClientPacketListener) (Object) this).getLevel(),
                packet.getX(),
                packet.getZ()
        );
    }
}
