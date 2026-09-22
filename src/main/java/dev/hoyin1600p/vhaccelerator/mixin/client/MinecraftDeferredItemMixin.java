package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.DeferredItemModelBaking;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Debug-only deferred item-model snapshots after rendered frames. Never bakes:
 * deferred models resolve only on first use, including after level changes.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftDeferredItemMixin {
    @Inject(method = "runTick", at = @At("TAIL"))
    private void vhaccelerator$observeDeferredItems(
            boolean renderLevel,
            CallbackInfo callback
    ) {
        DeferredItemModelBaking.observeFrame((Minecraft) (Object) this);
    }
}
