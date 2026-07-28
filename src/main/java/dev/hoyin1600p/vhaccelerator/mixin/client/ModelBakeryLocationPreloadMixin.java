package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.ParallelBlockStateModelLocations;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelBakery.class, priority = 1_200)
public abstract class ModelBakeryLocationPreloadMixin {
    @Inject(method = "processLoading", at = @At("HEAD"), remap = false)
    private void vhaccelerator$prepareModelLocations(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        ParallelBlockStateModelLocations.prepare();
    }
}
