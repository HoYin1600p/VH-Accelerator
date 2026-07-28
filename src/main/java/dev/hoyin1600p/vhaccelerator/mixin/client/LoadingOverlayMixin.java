package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.LaunchTimer;
import dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters.DeferredVaultAtlasUploads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow
    @Final
    private ReloadInstance reload;

    @Unique
    private boolean vhaccelerator$completionRecorded;

    @Inject(method = "render", at = @At("HEAD"))
    private void vhaccelerator$recordReloadCompletion(CallbackInfo callback) {
        if (!vhaccelerator$completionRecorded && reload.isDone()) {
            vhaccelerator$completionRecorded = true;
            LaunchTimer.markEnd();
        }
        if (reload.isDone()) {
            DeferredVaultAtlasUploads.processLoadingOverlayFrame();
        }
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;setOverlay(Lnet/minecraft/client/gui/screens/Overlay;)V"
            )
    )
    private void vhaccelerator$waitForDeferredVaultAtlases(
            Minecraft minecraft,
            Overlay requestedOverlay
    ) {
        if (requestedOverlay == null
                && DeferredVaultAtlasUploads.hasPendingUploads()) {
            return;
        }
        minecraft.setOverlay(requestedOverlay);
    }
}
