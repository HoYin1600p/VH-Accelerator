package dev.hoyin1600p.vhaccelerator.mixin;

import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.ParallelBlockStateInitializer;
import java.util.concurrent.atomic.AtomicInteger;
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
    @Unique
    private static final ResourceLocation LAUNCHFASTERTOO$BLOCK_REGISTRY =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block");
    @Unique
    private static final AtomicInteger LAUNCHFASTERTOO$VALIDATION_CALLS = new AtomicInteger();

    @Shadow
    private ResourceLocation name;

    @Inject(method = "validateContent", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$optionallySkipValidation(
            ResourceLocation registryName,
            CallbackInfo callback
    ) {
        if (!VHAcceleratorConfig.COMMON.enableCommonOptimizations.get()
                || !VHAcceleratorConfig.COMMON.skipRedundantRegistryValidation.get()) {
            return;
        }

        // Compatibility implementation of LaunchFaster's behavior. This is
        // intentionally disabled by default because the calls are global, not
        // three consecutive calls on the same registry instance.
        if (LAUNCHFASTERTOO$VALIDATION_CALLS.incrementAndGet() % 3 != 0) {
            callback.cancel();
        }
    }

    @Inject(method = "dump", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$skipRegistryDump(
            ResourceLocation registryName,
            CallbackInfo callback
    ) {
        if (VHAcceleratorConfig.COMMON.enableCommonOptimizations.get()
                && VHAcceleratorConfig.COMMON.skipRegistryDump.get()) {
            callback.cancel();
        }
    }

    @Inject(method = "bake", at = @At("HEAD"))
    private void vhaccelerator$beginBlockStateCollection(CallbackInfo callback) {
        if (!VHAcceleratorConfig.COMMON.enableCommonOptimizations.get()
                || !LAUNCHFASTERTOO$BLOCK_REGISTRY.equals(name)) {
            return;
        }

        if (VHAcceleratorConfig.COMMON.lazyBlockStateCache.get()
                || VHAcceleratorConfig.COMMON.parallelBlockStateInit.get()) {
            ParallelBlockStateInitializer.startCollecting();
        }
    }

    @Inject(method = "bake", at = @At("TAIL"))
    private void vhaccelerator$finishBlockStateCollection(CallbackInfo callback) {
        if (!LAUNCHFASTERTOO$BLOCK_REGISTRY.equals(name)) {
            return;
        }

        if (VHAcceleratorConfig.COMMON.lazyBlockStateCache.get()) {
            ParallelBlockStateInitializer.discardCollectedStates();
        } else if (VHAcceleratorConfig.COMMON.enableCommonOptimizations.get()
                && VHAcceleratorConfig.COMMON.parallelBlockStateInit.get()) {
            ParallelBlockStateInitializer.flushParallel();
        } else {
            ParallelBlockStateInitializer.discardCollectedStates();
        }
    }
}
