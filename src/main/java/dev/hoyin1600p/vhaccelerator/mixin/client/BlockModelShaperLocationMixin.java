package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.model.BlockStateModelLocationHolder;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModelShaper.class)
public abstract class BlockModelShaperLocationMixin {
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
            callback.setReturnValue(cached);
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
            callback.setReturnValue(cached);
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
