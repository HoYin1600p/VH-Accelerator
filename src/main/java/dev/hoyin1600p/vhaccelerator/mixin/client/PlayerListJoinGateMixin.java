package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.IntegratedServerJoinGate;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arms the singleplayer join hold; see IntegratedServerJoinGate. */
@Mixin(PlayerList.class)
public abstract class PlayerListJoinGateMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void vhaccelerator$holdUntilClientProcessed(
            Connection connection,
            ServerPlayer player,
            CallbackInfo callback
    ) {
        IntegratedServerJoinGate.playerPlaced(server, connection, player);
    }
}
