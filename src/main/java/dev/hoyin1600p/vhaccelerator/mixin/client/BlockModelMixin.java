package dev.hoyin1600p.vhaccelerator.mixin.client;

import com.mojang.datafixers.util.Pair;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.model.DynamicModelGuard;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModel.class)
public abstract class BlockModelMixin {
    @Unique
    private volatile Collection<Material> vhaccelerator$cachedMaterials;

    @Inject(method = "getMaterials", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$returnCachedMaterials(
            Function<ResourceLocation, UnbakedModel> modelGetter,
            Set<Pair<String, String>> missingTextureErrors,
            CallbackInfoReturnable<Collection<Material>> callback
    ) {
        if (!vhaccelerator$memoizationEnabled()
                || vhaccelerator$requiresLiveMaterialLookup(modelGetter)) {
            return;
        }

        Collection<Material> cached = vhaccelerator$cachedMaterials;
        if (cached != null) {
            callback.setReturnValue(cached);
        }
    }

    @Inject(method = "getMaterials", at = @At("RETURN"))
    private void vhaccelerator$cacheMaterials(
            Function<ResourceLocation, UnbakedModel> modelGetter,
            Set<Pair<String, String>> missingTextureErrors,
            CallbackInfoReturnable<Collection<Material>> callback
    ) {
        if (vhaccelerator$memoizationEnabled()
                && !vhaccelerator$requiresLiveMaterialLookup(modelGetter)
                && vhaccelerator$cachedMaterials == null
                && callback.getReturnValue() != null) {
            vhaccelerator$cachedMaterials =
                    List.copyOf(callback.getReturnValue());
        }
    }

    @Unique
    private boolean vhaccelerator$requiresLiveMaterialLookup(
            Function<ResourceLocation, UnbakedModel> modelGetter
    ) {
        return VHAcceleratorClientConfig.VALUES.protectDynamicModels.get()
                && DynamicModelGuard.requiresSequentialBaking(
                        (UnbakedModel) (Object) this,
                        modelGetter
                );
    }

    @Unique
    private static boolean vhaccelerator$memoizationEnabled() {
        return VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.VALUES.memoizeModelMaterials.get();
    }
}
