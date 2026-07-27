package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.cache.LoginStateFingerprint;
import java.util.function.Supplier;
import net.minecraftforge.network.ConfigSync;
import net.minecraftforge.network.HandshakeMessages;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ConfigSync.class, remap = false)
public abstract class ForgeConfigSyncMixin {
    @Inject(method = "receiveSyncedConfig", at = @At("HEAD"))
    private void vhaccelerator$captureServerConfig(
            HandshakeMessages.S2CConfigData config,
            Supplier<NetworkEvent.Context> context,
            CallbackInfo callback
    ) {
        LoginStateFingerprint.captureServerConfig(
                config.getFileName(),
                config.getBytes()
        );
    }
}
