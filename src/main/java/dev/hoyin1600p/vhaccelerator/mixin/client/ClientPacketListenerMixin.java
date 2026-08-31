package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.ServerLoginTimer;
import dev.hoyin1600p.vhaccelerator.client.ServerTransferTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClient;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.LoginStateFingerprint;
import dev.hoyin1600p.vhaccelerator.client.compat.thermal.ThermalRefreshPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Owns functional packet lifecycle hooks used by persistent caches, Thermal
 * refresh coordination, and cluster transfer timing. Debug-only packet
 * profiling lives in {@link ClientPacketListenerDiagnosticsMixin} so normal
 * clients do not weave those hooks.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleUpdateRecipes", at = @At("HEAD"))
    private void vhaccelerator$beginRecipeUpdate(
            ClientboundUpdateRecipesPacket packet,
            CallbackInfo callback
    ) {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }

        ThermalRefreshPhase.beginRecipes();
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || (!VHAcceleratorClientConfig.VALUES
                                .persistentVanillaRecipeValidationCache
                                .get()
                        && !VHAcceleratorClientConfig.VALUES
                                .persistentJeiRecipeIndexCache
                                .get())) {
            return;
        }
        LoginStateFingerprint.captureRecipePacket(packet);
    }

    @Inject(method = "handleUpdateRecipes", at = @At("RETURN"))
    private void vhaccelerator$finishRecipeUpdate(
            ClientboundUpdateRecipesPacket packet,
            CallbackInfo callback
    ) {
        if (Minecraft.getInstance().isSameThread()) {
            ThermalRefreshPhase.finishRecipes();
        }
    }

    @Inject(method = "handleUpdateTags", at = @At("HEAD"))
    private void vhaccelerator$beginTagUpdate(
            ClientboundUpdateTagsPacket packet,
            CallbackInfo callback
    ) {
        if (Minecraft.getInstance().isSameThread()) {
            ThermalRefreshPhase.beginTags();
            LoginStateFingerprint.captureCanonicalItemTags(packet);
        }
    }

    @Inject(method = "handleUpdateTags", at = @At("RETURN"))
    private void vhaccelerator$finishTagUpdate(
            ClientboundUpdateTagsPacket packet,
            CallbackInfo callback
    ) {
        if (Minecraft.getInstance().isSameThread()) {
            ThermalRefreshPhase.finishTags();
        }
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void vhaccelerator$startTransferTimer(
            ClientboundRespawnPacket packet,
            CallbackInfo callback
    ) {
        if (Minecraft.getInstance().isSameThread()
                && !ServerLoginTimer.isActive()
                && ServerTransferTimer.markStart("respawn packet")) {
            VHAcceleratorClient.beginServerStateRefresh();
        }
    }
}
