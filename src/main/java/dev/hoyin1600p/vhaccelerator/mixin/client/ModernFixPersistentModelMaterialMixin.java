package dev.hoyin1600p.vhaccelerator.mixin.client;

import com.mojang.datafixers.util.Pair;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentModelMaterialCache;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Persistent-only material hook used when ModernFix supplies the in-memory
 * BlockModel memoization.
 */
@Mixin(BlockModel.class)
public abstract class ModernFixPersistentModelMaterialMixin {
    @Inject(method = "getMaterials", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$restorePersistentMaterials(
            Function<ResourceLocation, UnbakedModel> modelGetter,
            Set<Pair<String, String>> missingTextureErrors,
            CallbackInfoReturnable<Collection<Material>> callback
    ) {
        Collection<Material> materials =
                PersistentModelMaterialCache.restore(
                        (BlockModel) (Object) this,
                        modelGetter
                );
        if (materials != null) {
            callback.setReturnValue(materials);
        }
    }

    @Inject(method = "getMaterials", at = @At("RETURN"))
    private void vhaccelerator$capturePersistentMaterials(
            Function<ResourceLocation, UnbakedModel> modelGetter,
            Set<Pair<String, String>> missingTextureErrors,
            CallbackInfoReturnable<Collection<Material>> callback
    ) {
        PersistentModelMaterialCache.record(
                (BlockModel) (Object) this,
                modelGetter,
                callback.getReturnValue()
        );
    }
}
