package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.shape.LaunchShapeCache;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuses {@code optimize()} results during launch; see {@link LaunchShapeCache}. */
@Mixin(VoxelShape.class)
public abstract class VoxelShapeLaunchCacheMixin {
    @Unique
    private static volatile int vhaccelerator$state;

    @Inject(method = "optimize", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$reuseOptimized(CallbackInfoReturnable<VoxelShape> callback) {
        if (vhaccelerator$enabled()) {
            Object optimized = LaunchShapeCache.optimized(this);
            if (optimized != null) {
                callback.setReturnValue((VoxelShape) optimized);
            }
        }
    }

    @Inject(method = "optimize", at = @At("RETURN"))
    private void vhaccelerator$storeOptimized(CallbackInfoReturnable<VoxelShape> callback) {
        if (vhaccelerator$enabled()) {
            LaunchShapeCache.storeOptimized(this, callback.getReturnValue());
        }
    }

    @Unique
    private static boolean vhaccelerator$enabled() {
        if (!LaunchShapeCache.isOpen()) {
            return false;
        }
        int cached = vhaccelerator$state;
        if (cached != 0) {
            return cached == 2;
        }
        if (!VHAcceleratorClientConfig.launchSnapshotCaptured()) {
            return false;
        }
        boolean enabled = VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.cacheLaunchVoxelShapes
                );
        vhaccelerator$state = enabled ? 2 : 1;
        return enabled;
    }
}
