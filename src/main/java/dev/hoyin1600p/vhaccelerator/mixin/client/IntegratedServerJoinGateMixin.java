package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.IntegratedServerJoinGate;
import java.util.function.BooleanSupplier;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds world ticks during the singleplayer join; the connection is still
 * ticked so client packets are handled. Returning before the vanilla pause
 * check also skips its pause autosave.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerJoinGateMixin {
    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$holdDuringJoin(BooleanSupplier hasTimeLeft, CallbackInfo callback) {
        IntegratedServer server = (IntegratedServer) (Object) this;
        if (IntegratedServerJoinGate.holdTick(server)) {
            ServerConnectionListener connection = server.getConnection();
            if (connection != null) {
                connection.tick();
            }
            callback.cancel();
        }
    }
}
