package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.model.DeferredItemModelBaking;
import dev.hoyin1600p.vhaccelerator.client.model.DeferredItemModelOwner;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.AtlasSet;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Selects deferred inventory models for the top-level bake pass and publishes
 * the deferred registry once the eager pass finishes. The top-level bake
 * redirects exclude the selection; if neither redirect ran, nothing was
 * selected and every model was baked eagerly.
 */
@Mixin(ModelBakery.class)
public abstract class ModelBakeryDeferredItemMixin implements DeferredItemModelOwner {
    @Shadow
    @Final
    @Mutable
    private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

    @Shadow
    @Final
    private Map<ResourceLocation, UnbakedModel> topLevelModels;

    @Shadow
    @Final
    private Map<ResourceLocation, UnbakedModel> unbakedCache;

    @Shadow
    @Nullable
    public abstract BakedModel bake(ResourceLocation location, ModelState state);

    @Unique
    private Set<ResourceLocation> vhaccelerator$deferredItems;

    @Override
    public Set<ResourceLocation> vhaccelerator$deferredItemModels() {
        Set<ResourceLocation> selected = vhaccelerator$deferredItems;
        if (selected != null) {
            return selected;
        }
        selected = Collections.emptySet();
        try {
            if (DeferredItemModelBaking.activeForThisBake()) {
                selected = Collections.unmodifiableSet(
                        DeferredItemModelBaking.select(
                                topLevelModels,
                                unbakedCache
                        )
                );
            }
        } catch (RuntimeException | LinkageError failure) {
            selected = Collections.emptySet();
            VHAccelerator.LOGGER.warn(
                    "Could not select deferred item models; baking all "
                            + "models eagerly",
                    failure
            );
        }
        vhaccelerator$deferredItems = selected;
        return selected;
    }

    @Inject(method = "uploadTextures", at = @At("RETURN"))
    private void vhaccelerator$publishDeferredItems(
            TextureManager textureManager,
            ProfilerFiller profiler,
            CallbackInfoReturnable<AtlasSet> callback
    ) {
        Set<ResourceLocation> deferred = vhaccelerator$deferredItems;
        if (deferred == null || deferred.isEmpty()) {
            return;
        }
        bakedTopLevelModels = DeferredItemModelBaking.install(
                bakedTopLevelModels,
                deferred,
                location -> bake(location, BlockModelRotation.X0_Y0)
        );
    }
}
