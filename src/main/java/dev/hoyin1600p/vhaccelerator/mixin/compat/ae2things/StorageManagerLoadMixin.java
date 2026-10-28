package dev.hoyin1600p.vhaccelerator.mixin.compat.ae2things;

import dev.hoyin1600p.vhaccelerator.compat.ae2things.Ae2ThingsDiskStrings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs on the manager AE2Things has just built from disk, before Minecraft publishes it. */
@Pseudo
@Mixin(targets = "io.github.projectet.ae2things.util.StorageManager", remap = false)
public abstract class StorageManagerLoadMixin {
    @Inject(method = "readNbt(Lnet/minecraft/nbt/CompoundTag;)Lio/github/projectet/ae2things/util/StorageManager;",
            at = @At("RETURN"), require = 0, remap = false)
    private static void vhaccelerator$shareDiskStrings(CallbackInfoReturnable<Object> callback) {
        Ae2ThingsDiskStrings.shareLoaded(callback.getReturnValue());
    }
}
