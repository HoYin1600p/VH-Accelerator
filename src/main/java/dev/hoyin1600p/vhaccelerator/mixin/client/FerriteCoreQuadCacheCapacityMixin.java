package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.cache.FerriteCoreQuadCacheCapacity;
import dev.hoyin1600p.vhaccelerator.client.model.ModelCacheSizing;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBakery.class)
public abstract class FerriteCoreQuadCacheCapacityMixin {
    @Inject(
            method = "processLoading",
            at = @At("HEAD"),
            remap = false
    )
    private void vhaccelerator$prepareFerriteCoreCapacity(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        FerriteCoreQuadCacheCapacity.prepare(
                ModelCacheSizing.topLevelEstimate()
        );
    }
}
