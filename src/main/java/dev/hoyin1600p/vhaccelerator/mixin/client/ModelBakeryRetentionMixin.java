package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.BakeryRetention;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/** Read-only view of the bakery's retained maps for debug reporting. */
@Mixin(ModelBakery.class)
public abstract class ModelBakeryRetentionMixin implements BakeryRetention {
    @Shadow
    @Final
    @Mutable
    private Map<ResourceLocation, UnbakedModel> unbakedCache;

    @Shadow
    @Final
    @Mutable
    private Map<?, BakedModel> bakedCache;

    @Shadow
    @Final
    @Mutable
    private Map<ResourceLocation, UnbakedModel> topLevelModels;

    @Shadow
    @Final
    @Mutable
    private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

    @Override
    public Map<?, ?> vhaccelerator$unbakedCache() {
        return unbakedCache;
    }

    @Override
    public Map<?, ?> vhaccelerator$bakedCache() {
        return bakedCache;
    }

    @Override
    public Map<?, ?> vhaccelerator$topLevelModels() {
        return topLevelModels;
    }

    @Override
    public Map<?, ?> vhaccelerator$bakedTopLevelModels() {
        return bakedTopLevelModels;
    }

    @Override
    public int vhaccelerator$releaseLoadMaps(boolean includeBakedCache) {
        // Replace rather than clear: HashMap#clear keeps its table array.
        int released = topLevelModels.size();
        topLevelModels = new HashMap<>();
        if (includeBakedCache) {
            released += bakedCache.size();
            bakedCache = new HashMap<>();
        }
        return released;
    }
}
