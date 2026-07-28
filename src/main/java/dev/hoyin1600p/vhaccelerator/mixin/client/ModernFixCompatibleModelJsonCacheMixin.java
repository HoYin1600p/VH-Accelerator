package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentModelJsonCache;
import java.io.StringReader;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Uses the persistent raw JSON cache only when ModernFix is present and its
 * dynamic-resources ModelBakery replacement is disabled.
 */
@Mixin(ModelBakery.class)
public abstract class ModernFixCompatibleModelJsonCacheMixin {
    @Shadow
    @Final
    protected ResourceManager resourceManager;

    @Unique
    private PersistentModelJsonCache.Session
            vhaccelerator$modelCacheSession;

    @Inject(method = "processLoading", at = @At("HEAD"), remap = false)
    private void vhaccelerator$openPersistentModelCache(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$modelCacheSession =
                PersistentModelJsonCache.prepare(resourceManager);
    }

    @Inject(method = "loadBlockModel", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$loadPersistentModelJson(
            ResourceLocation location,
            CallbackInfoReturnable<BlockModel> callback
    ) {
        PersistentModelJsonCache.Session session =
                vhaccelerator$modelCacheSession;
        if (session == null
                || location.getPath().startsWith("builtin/")) {
            return;
        }
        Map<ResourceLocation, String> models = session.models();
        ResourceLocation resourcePath =
                ResourceLocation.fromNamespaceAndPath(
                        location.getNamespace(),
                        "models/" + location.getPath() + ".json"
                );
        String json = models.get(resourcePath);
        if (json == null) {
            return;
        }
        try {
            BlockModel model =
                    BlockModel.fromStream(new StringReader(json));
            model.name = location.toString();
            callback.setReturnValue(model);
        } catch (RuntimeException | LinkageError failure) {
            session.invalidate();
            VHAccelerator.LOGGER.warn(
                    "Cached model JSON {} could not be parsed; retrying "
                            + "through the active resource manager",
                    location,
                    failure
            );
        }
    }

    @Inject(method = "processLoading", at = @At("TAIL"), remap = false)
    private void vhaccelerator$closePersistentModelCache(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        PersistentModelJsonCache.finish(
                vhaccelerator$modelCacheSession
        );
        vhaccelerator$modelCacheSession = null;
    }
}
