package dev.hoyin1600p.launchfastertoo.mixin.client;

import dev.hoyin1600p.launchfastertoo.client.LaunchTimer;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow
    @Final
    private ReloadInstance reload;

    @Unique
    private boolean launchfastertoo$completionRecorded;

    @Inject(method = "render", at = @At("HEAD"))
    private void launchfastertoo$recordReloadCompletion(CallbackInfo callback) {
        if (!launchfastertoo$completionRecorded && reload.isDone()) {
            launchfastertoo$completionRecorded = true;
            LaunchTimer.markEnd();
        }
    }
}

