package dev.hoyin1600p.vhaccelerator.mixin.registry;

import dev.hoyin1600p.vhaccelerator.registry.ObjectHolderDispatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.GameData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Names the registry whose holder lookups Forge is about to apply. */
@Mixin(value = GameData.class, remap = false)
public abstract class GameDataHolderAnnounceMixin {
    @Inject(method = "applyHolderLookups", at = @At("RETURN"), require = 0, remap = false)
    private static void vhaccelerator$clearHolderAnnouncement(ResourceLocation registryName, CallbackInfo callback) {
        ObjectHolderDispatch.clearAnnouncement();
    }

    @Inject(method = "applyHolderLookups", at = @At("HEAD"), require = 0, remap = false)
    private static void vhaccelerator$announceHolderRegistry(ResourceLocation registryName, CallbackInfo callback) {
        ObjectHolderDispatch.announce(registryName);
    }
}
