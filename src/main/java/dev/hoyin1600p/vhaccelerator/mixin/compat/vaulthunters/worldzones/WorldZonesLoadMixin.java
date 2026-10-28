package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters.worldzones;

import dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesCompaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "iskallia.vault.core.world.storage.WorldZones", remap = false)
public abstract class WorldZonesLoadMixin {
    @Inject(method = {"readNbt(Lnet/minecraft/nbt/CompoundTag;)V", "readBits(Liskallia/vault/core/net/BitBuffer;)V"},
            at = @At("RETURN"), require = 0, remap = false)
    private void vhaccelerator$compactLoadedMemberships(CallbackInfo callback) {
        WorldZonesCompaction.compact(this);
    }
}
