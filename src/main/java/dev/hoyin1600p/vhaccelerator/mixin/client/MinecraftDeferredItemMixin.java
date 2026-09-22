package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.DeferredItemModelBaking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Warms deferred item models off-world and finishes them before any level renders. */
@Mixin(Minecraft.class)
public abstract class MinecraftDeferredItemMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void vhaccelerator$warmDeferredItems(CallbackInfo callback) {
        DeferredItemModelBaking.tick((Minecraft) (Object) this);
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void vhaccelerator$finishDeferredItems(
            ClientLevel level,
            CallbackInfo callback
    ) {
        DeferredItemModelBaking.drainBeforeLevel((Minecraft) (Object) this);
    }
}
