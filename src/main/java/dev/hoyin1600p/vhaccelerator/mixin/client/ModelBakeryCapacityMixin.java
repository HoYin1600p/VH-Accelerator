package dev.hoyin1600p.vhaccelerator.mixin.client;

import com.mojang.math.Transformation;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.model.ModelCacheSizing;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.commons.lang3.tuple.Triple;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Avoids repeated growth and full-table rehashes in model-heavy packs while
 * leaving specialized maps installed by other mods untouched.
 */
@Mixin(ModelBakery.class)
public abstract class ModelBakeryCapacityMixin {
    @Shadow
    @Final
    @Mutable
    private Map<ResourceLocation, UnbakedModel> unbakedCache;

    @Shadow
    @Final
    @Mutable
    private Map<
            Triple<ResourceLocation, Transformation, Boolean>,
            BakedModel> bakedCache;

    @Shadow
    @Final
    @Mutable
    private Map<ResourceLocation, UnbakedModel> topLevelModels;

    @Shadow
    @Final
    @Mutable
    private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

    @Inject(method = "processLoading", at = @At("HEAD"), remap = false)
    private void vhaccelerator$sizeModelCaches(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .preSizeModelCaches
                )) {
            return;
        }

        int topLevelEstimate = ModelCacheSizing.topLevelEstimate();
        int unbakedEstimate = vhaccelerator$saturatingAdd(
                topLevelEstimate,
                Math.max(4_096, topLevelEstimate / 16)
        );
        int replaced = 0;

        Map<ResourceLocation, UnbakedModel> sizedUnbaked =
                vhaccelerator$replacePlainEmptyMap(
                        unbakedCache,
                        unbakedEstimate
                );
        if (sizedUnbaked != unbakedCache) {
            unbakedCache = sizedUnbaked;
            replaced++;
        }

        Map<
                Triple<ResourceLocation, Transformation, Boolean>,
                BakedModel> sizedBaked =
                vhaccelerator$replacePlainEmptyMap(
                        bakedCache,
                        topLevelEstimate
                );
        if (sizedBaked != bakedCache) {
            bakedCache = sizedBaked;
            replaced++;
        }

        Map<ResourceLocation, UnbakedModel> sizedTopLevel =
                vhaccelerator$replacePlainEmptyMap(
                        topLevelModels,
                        topLevelEstimate
                );
        if (sizedTopLevel != topLevelModels) {
            topLevelModels = sizedTopLevel;
            replaced++;
        }

        Map<ResourceLocation, BakedModel> sizedBakedTopLevel =
                vhaccelerator$replacePlainEmptyMap(
                        bakedTopLevelModels,
                        topLevelEstimate
                );
        if (sizedBakedTopLevel != bakedTopLevelModels) {
            bakedTopLevelModels = sizedBakedTopLevel;
            replaced++;
        }

        VHAccelerator.LOGGER.info(
                "Sized {} plain ModelBakery map(s) for approximately {} "
                        + "top-level model entries",
                replaced,
                topLevelEstimate
        );
    }

    @Unique
    private static int vhaccelerator$saturatingAdd(
            int left,
            int right
    ) {
        long value = (long) left + right;
        return value >= Integer.MAX_VALUE - 8L
                ? Integer.MAX_VALUE - 8
                : (int) value;
    }

    @Unique
    private static <K, V> Map<K, V>
            vhaccelerator$replacePlainEmptyMap(
                    Map<K, V> existing,
                    int expectedEntries
            ) {
        if (!existing.isEmpty()
                || existing.getClass() != HashMap.class) {
            return existing;
        }
        return new HashMap<>(
                vhaccelerator$hashMapCapacity(expectedEntries)
        );
    }

    @Unique
    private static int vhaccelerator$hashMapCapacity(
            int expectedEntries
    ) {
        if (expectedEntries < 3) {
            return expectedEntries + 1;
        }
        if (expectedEntries >= 1 << 29) {
            return 1 << 30;
        }
        return (int) (expectedEntries / 0.75F + 1.0F);
    }
}
