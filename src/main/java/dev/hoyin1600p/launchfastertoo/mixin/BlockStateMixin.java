package dev.hoyin1600p.launchfastertoo.mixin;

import dev.hoyin1600p.launchfastertoo.LaunchFasterTooConfig;
import dev.hoyin1600p.launchfastertoo.ParallelBlockStateInitializer;
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
    private volatile boolean launchfastertoo$cacheDeferred;

    @Inject(method = "initCache", at = @At("HEAD"), cancellable = true)
    private void launchfastertoo$deferCacheInitialization(CallbackInfo callback) {
        if (!ParallelBlockStateInitializer.isCollecting()) {
            return;
        }

        if (LaunchFasterTooConfig.COMMON.lazyBlockStateCache.get()) {
            launchfastertoo$cacheDeferred = true;
            callback.cancel();
        } else if (LaunchFasterTooConfig.COMMON.parallelBlockStateInit.get()) {
            ParallelBlockStateInitializer.collect((BlockState) (Object) this);
            callback.cancel();
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"))
    private void launchfastertoo$initializeForFluidState(
            CallbackInfoReturnable<?> callback
    ) {
        launchfastertoo$ensureCacheInitialized();
    }

    @Inject(method = "propagatesSkylightDown", at = @At("HEAD"))
    private void launchfastertoo$initializeForSkylight(
            CallbackInfoReturnable<Boolean> callback
    ) {
        launchfastertoo$ensureCacheInitialized();
    }

    @Inject(method = "getLightBlock", at = @At("HEAD"))
    private void launchfastertoo$initializeForLightBlock(
            CallbackInfoReturnable<Integer> callback
    ) {
        launchfastertoo$ensureCacheInitialized();
    }

    @Unique
    private void launchfastertoo$ensureCacheInitialized() {
        if (!launchfastertoo$cacheDeferred) {
            return;
        }

        synchronized (this) {
            if (!launchfastertoo$cacheDeferred) {
                return;
            }
            launchfastertoo$cacheDeferred = false;
            ((BlockBehaviour.BlockStateBase) (Object) this).initCache();
        }
    }
}

