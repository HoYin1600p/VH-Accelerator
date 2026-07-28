package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.model.BlockStateModelLocationHolder;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModelShaper.class)
public abstract class BlockModelShaperLocationMixin {
    @Unique
    private static final AtomicLong
            VHACCELERATOR$LOCATION_HITS = new AtomicLong();
    @Unique
    private static final AtomicLong
            VHACCELERATOR$LOCATION_MISSES = new AtomicLong();
    @Unique
    private long vhaccelerator$hitsBeforeRebuild;
    @Unique
    private long vhaccelerator$missesBeforeRebuild;
    @Unique
    private long vhaccelerator$rebuildStarted;

    @Inject(
            method = "stateToModelLocation("
                    + "Lnet/minecraft/world/level/block/state/BlockState;)"
                    + "Lnet/minecraft/client/resources/model/"
                    + "ModelResourceLocation;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void vhaccelerator$reuseModelLocation(
            BlockState state,
            CallbackInfoReturnable<ModelResourceLocation> callback
    ) {
        if (!vhaccelerator$locationCacheEnabled()) {
            return;
        }
        ModelResourceLocation cached =
                ((BlockStateModelLocationHolder) state)
                        .vhaccelerator$getModelLocation();
        if (cached != null) {
            VHACCELERATOR$LOCATION_HITS.incrementAndGet();
            callback.setReturnValue(cached);
        } else {
            VHACCELERATOR$LOCATION_MISSES.incrementAndGet();
        }
    }

    @Inject(
            method = "stateToModelLocation("
                    + "Lnet/minecraft/world/level/block/state/BlockState;)"
                    + "Lnet/minecraft/client/resources/model/"
                    + "ModelResourceLocation;",
            at = @At("RETURN")
    )
    private static void vhaccelerator$rememberModelLocation(
            BlockState state,
            CallbackInfoReturnable<ModelResourceLocation> callback
    ) {
        if (!vhaccelerator$locationCacheEnabled()) {
            return;
        }
        BlockStateModelLocationHolder holder =
                (BlockStateModelLocationHolder) state;
        if (holder.vhaccelerator$getModelLocation() == null) {
            holder.vhaccelerator$setModelLocation(
                    callback.getReturnValue()
            );
        }
    }

    @Inject(
            method = "stateToModelLocation("
                    + "Lnet/minecraft/resources/ResourceLocation;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;)"
                    + "Lnet/minecraft/client/resources/model/"
                    + "ModelResourceLocation;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void vhaccelerator$reuseCanonicalModelLocation(
            ResourceLocation location,
            BlockState state,
            CallbackInfoReturnable<ModelResourceLocation> callback
    ) {
        if (!vhaccelerator$locationCacheEnabled()
                || !vhaccelerator$isCanonical(location, state)) {
            return;
        }
        ModelResourceLocation cached =
                ((BlockStateModelLocationHolder) state)
                        .vhaccelerator$getModelLocation();
        if (cached != null) {
            VHACCELERATOR$LOCATION_HITS.incrementAndGet();
            callback.setReturnValue(cached);
        } else {
            VHACCELERATOR$LOCATION_MISSES.incrementAndGet();
        }
    }

    @Inject(
            method = "stateToModelLocation("
                    + "Lnet/minecraft/resources/ResourceLocation;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;)"
                    + "Lnet/minecraft/client/resources/model/"
                    + "ModelResourceLocation;",
            at = @At("RETURN")
    )
    private static void vhaccelerator$rememberCanonicalModelLocation(
            ResourceLocation location,
            BlockState state,
            CallbackInfoReturnable<ModelResourceLocation> callback
    ) {
        if (!vhaccelerator$locationCacheEnabled()
                || !vhaccelerator$isCanonical(location, state)) {
            return;
        }
        BlockStateModelLocationHolder holder =
                (BlockStateModelLocationHolder) state;
        if (holder.vhaccelerator$getModelLocation() == null) {
            holder.vhaccelerator$setModelLocation(
                    callback.getReturnValue()
            );
        }
    }

    @Inject(method = "rebuildCache", at = @At("HEAD"))
    private void vhaccelerator$beginLocationMeasurement(
            CallbackInfo callback
    ) {
        if (!vhaccelerator$locationCacheEnabled()) {
            return;
        }
        vhaccelerator$hitsBeforeRebuild =
                VHACCELERATOR$LOCATION_HITS.get();
        vhaccelerator$missesBeforeRebuild =
                VHACCELERATOR$LOCATION_MISSES.get();
        vhaccelerator$rebuildStarted = System.nanoTime();
    }

    @Inject(method = "rebuildCache", at = @At("TAIL"))
    private void vhaccelerator$reportLocationReuse(
            CallbackInfo callback
    ) {
        if (!vhaccelerator$locationCacheEnabled()
                || vhaccelerator$rebuildStarted == 0L) {
            return;
        }
        long hits = VHACCELERATOR$LOCATION_HITS.get()
                - vhaccelerator$hitsBeforeRebuild;
        long misses = VHACCELERATOR$LOCATION_MISSES.get()
                - vhaccelerator$missesBeforeRebuild;
        long elapsedMillis = (System.nanoTime()
                - vhaccelerator$rebuildStarted) / 1_000_000L;
        vhaccelerator$rebuildStarted = 0L;
        VHAccelerator.LOGGER.info(
                "Rebuilt block model lookup in {} ms "
                        + "[{} model-location cache hits, {} misses]",
                elapsedMillis,
                hits,
                misses
        );
    }

    @Unique
    private static boolean vhaccelerator$locationCacheEnabled() {
        return VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.cacheBlockStateModelLocations
                );
    }

    @Unique
    private static boolean vhaccelerator$isCanonical(
            ResourceLocation location,
            BlockState state
    ) {
        return location.equals(
                Registry.BLOCK.getKey(state.getBlock())
        );
    }
}
