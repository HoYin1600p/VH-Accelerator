package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.cache.PersistentModelMaterialCache;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBakery.class)
public abstract class ModelMaterialCacheSessionMixin {
    @Shadow
    @Final
    protected ResourceManager resourceManager;

    @Inject(method = "processLoading", at = @At("HEAD"), remap = false)
    private void vhaccelerator$beginMaterialCache(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        PersistentModelMaterialCache.begin(resourceManager);
    }

    @Inject(method = "processLoading", at = @At("TAIL"), remap = false)
    private void vhaccelerator$finishMaterialCache(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        PersistentModelMaterialCache.finish();
    }
}
