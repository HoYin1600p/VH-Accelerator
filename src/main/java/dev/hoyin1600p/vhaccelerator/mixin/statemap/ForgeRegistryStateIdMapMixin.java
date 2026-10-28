package dev.hoyin1600p.vhaccelerator.mixin.statemap;

import dev.hoyin1600p.vhaccelerator.registry.StateIdMapPresize;
import net.minecraftforge.registries.ForgeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sizes the block-state id map right before the block registry's first bake fills it. Does nothing when
 * {@link IdMapperSlotsMixin} did not apply or the map is not the reviewed layout.
 */
@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class ForgeRegistryStateIdMapMixin {
    @Inject(method = "bake", at = @At("HEAD"), require = 0)
    private void vhaccelerator$sizeStateIdMap(CallbackInfo callback) {
        StateIdMapPresize.beforeBlockBake((ForgeRegistry<?>) (Object) this);
    }
}
