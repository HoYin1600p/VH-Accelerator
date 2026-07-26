package dev.hoyin1600p.launchfastertoo.mixin.client;

import com.mojang.datafixers.util.Pair;
import dev.hoyin1600p.launchfastertoo.client.LaunchFasterTooClientConfig;
import java.util.Collection;
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
    private volatile Collection<Material> launchfastertoo$cachedMaterials;

    @Inject(method = "getMaterials", at = @At("HEAD"), cancellable = true)
    private void launchfastertoo$returnCachedMaterials(
            Function<ResourceLocation, UnbakedModel> modelGetter,
            Set<Pair<String, String>> missingTextureErrors,
            CallbackInfoReturnable<Collection<Material>> callback
    ) {
        if (!launchfastertoo$memoizationEnabled()) {
            return;
        }

        Collection<Material> cached = launchfastertoo$cachedMaterials;
        if (cached != null) {
            callback.setReturnValue(cached);
        }
    }

    @Inject(method = "getMaterials", at = @At("RETURN"))
    private void launchfastertoo$cacheMaterials(
            Function<ResourceLocation, UnbakedModel> modelGetter,
            Set<Pair<String, String>> missingTextureErrors,
            CallbackInfoReturnable<Collection<Material>> callback
    ) {
        if (launchfastertoo$memoizationEnabled()
                && launchfastertoo$cachedMaterials == null
                && callback.getReturnValue() != null) {
            launchfastertoo$cachedMaterials = callback.getReturnValue();
        }
    }

    @Unique
    private static boolean launchfastertoo$memoizationEnabled() {
        return LaunchFasterTooClientConfig.VALUES.enableClientOptimizations.get()
                && LaunchFasterTooClientConfig.VALUES.memoizeModelMaterials.get();
    }
}

