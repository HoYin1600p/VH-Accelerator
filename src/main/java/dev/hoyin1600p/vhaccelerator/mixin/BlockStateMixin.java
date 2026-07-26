package dev.hoyin1600p.vhaccelerator.mixin;

import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.ParallelBlockStateInitializer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateMixin {
    @Unique
    private volatile boolean vhaccelerator$cacheDeferred;

    @Inject(method = "initCache", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$deferCacheInitialization(CallbackInfo callback) {
        if (!ParallelBlockStateInitializer.isCollecting()) {
            return;
        }

        if (VHAcceleratorConfig.COMMON.lazyBlockStateCache.get()) {
            vhaccelerator$cacheDeferred = true;
            callback.cancel();
        } else if (VHAcceleratorConfig.COMMON.parallelBlockStateInit.get()) {
            ParallelBlockStateInitializer.collect((BlockState) (Object) this);
            callback.cancel();
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"))
    private void vhaccelerator$initializeForFluidState(
            CallbackInfoReturnable<?> callback
    ) {
        vhaccelerator$ensureCacheInitialized();
    }

    @Inject(method = "propagatesSkylightDown", at = @At("HEAD"))
    private void vhaccelerator$initializeForSkylight(
            CallbackInfoReturnable<Boolean> callback
    ) {
        vhaccelerator$ensureCacheInitialized();
    }

    @Inject(method = "getLightBlock", at = @At("HEAD"))
    private void vhaccelerator$initializeForLightBlock(
            CallbackInfoReturnable<Integer> callback
    ) {
        vhaccelerator$ensureCacheInitialized();
    }

    @Unique
    private void vhaccelerator$ensureCacheInitialized() {
        if (!vhaccelerator$cacheDeferred) {
            return;
        }

        synchronized (this) {
            if (!vhaccelerator$cacheDeferred) {
                return;
            }
            vhaccelerator$cacheDeferred = false;
            ((BlockBehaviour.BlockStateBase) (Object) this).initCache();
        }
    }
}

