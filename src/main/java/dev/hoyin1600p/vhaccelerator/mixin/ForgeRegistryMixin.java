package dev.hoyin1600p.vhaccelerator.mixin;

import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.ParallelBlockStateInitializer;
import dev.hoyin1600p.vhaccelerator.RegistryValidationState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class ForgeRegistryMixin {
    @Shadow
    private ResourceLocation name;

    @Inject(method = "validateContent", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$optionallySkipValidation(
            ResourceLocation registryName,
            CallbackInfo callback
    ) {
        if (!VHAcceleratorConfig.commonOptimizationsEnabled()
                || !VHAcceleratorConfig.COMMON.skipRedundantRegistryValidation.get()) {
            return;
        }

        // Compatibility implementation of LaunchFaster's behavior. This is
        // intentionally disabled by default because the calls are global, not
        // three consecutive calls on the same registry instance.
        if (RegistryValidationState.shouldSkipCurrentCall()) {
            callback.cancel();
        }
    }

    @Inject(method = "dump", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$skipRegistryDump(
            ResourceLocation registryName,
            CallbackInfo callback
    ) {
        if (VHAcceleratorConfig.commonOptimizationsEnabled()
                && VHAcceleratorConfig.COMMON.skipRegistryDump.get()) {
            callback.cancel();
        }
    }

    @Inject(method = "bake", at = @At("HEAD"))
    private void vhaccelerator$beginBlockStateCollection(CallbackInfo callback) {
        if (!VHAcceleratorConfig.commonOptimizationsEnabled()
                || !vhaccelerator$isBlockRegistry()) {
            return;
        }

        if (VHAcceleratorConfig.COMMON.lazyBlockStateCache.get()
                || VHAcceleratorConfig.COMMON.parallelBlockStateInit.get()) {
            ParallelBlockStateInitializer.startCollecting();
        }
    }

    @Inject(method = "bake", at = @At("TAIL"))
    private void vhaccelerator$finishBlockStateCollection(CallbackInfo callback) {
        if (!vhaccelerator$isBlockRegistry()) {
            return;
        }

        if (VHAcceleratorConfig.COMMON.lazyBlockStateCache.get()) {
            ParallelBlockStateInitializer.discardCollectedStates();
        } else if (VHAcceleratorConfig.commonOptimizationsEnabled()
                && VHAcceleratorConfig.COMMON.parallelBlockStateInit.get()) {
            ParallelBlockStateInitializer.flushParallel();
        } else {
            ParallelBlockStateInitializer.discardCollectedStates();
        }
    }

    @Unique
    private boolean vhaccelerator$isBlockRegistry() {
        return name != null
                && "minecraft".equals(name.getNamespace())
                && "block".equals(name.getPath());
    }
}
