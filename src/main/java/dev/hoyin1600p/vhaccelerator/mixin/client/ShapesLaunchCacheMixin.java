package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.shape.LaunchShapeCache;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shares equal boxes and reuses identical joins while mods build their block
 * shapes during launch. See {@link LaunchShapeCache}.
 */
@Mixin(Shapes.class)
public abstract class ShapesLaunchCacheMixin {
    @Unique
    private static volatile int vhaccelerator$state;

    @Inject(method = "box", at = @At("HEAD"), cancellable = true)
    private static void vhaccelerator$reuseBox(
            double x1, double y1, double z1, double x2, double y2, double z2,
            CallbackInfoReturnable<VoxelShape> callback
    ) {
        if (vhaccelerator$enabled()) {
            Object shared = LaunchShapeCache.box(x1, y1, z1, x2, y2, z2);
            if (shared != null) {
                callback.setReturnValue((VoxelShape) shared);
            }
        }
    }

    @Inject(method = "box", at = @At("RETURN"), cancellable = true)
    private static void vhaccelerator$shareBox(
            double x1, double y1, double z1, double x2, double y2, double z2,
            CallbackInfoReturnable<VoxelShape> callback
    ) {
        if (vhaccelerator$enabled()) {
            Object shared = LaunchShapeCache.storeBox(
                    x1, y1, z1, x2, y2, z2, callback.getReturnValue()
            );
            if (shared != callback.getReturnValue()) {
                callback.setReturnValue((VoxelShape) shared);
            }
        }
    }

    @Inject(method = "joinUnoptimized", at = @At("HEAD"), cancellable = true)
    private static void vhaccelerator$reuseJoin(
            VoxelShape first,
            VoxelShape second,
            BooleanOp operation,
            CallbackInfoReturnable<VoxelShape> callback
    ) {
        if (vhaccelerator$enabled()) {
            Object joined = LaunchShapeCache.join(first, second, operation);
            if (joined != null) {
                callback.setReturnValue((VoxelShape) joined);
            }
        }
    }

    @Inject(method = "joinUnoptimized", at = @At("RETURN"))
    private static void vhaccelerator$storeJoin(
            VoxelShape first,
            VoxelShape second,
            BooleanOp operation,
            CallbackInfoReturnable<VoxelShape> callback
    ) {
        if (vhaccelerator$enabled()) {
            LaunchShapeCache.storeJoin(first, second, operation, callback.getReturnValue());
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
        // Zero is the uncaptured state; see ShapesCoordinateMergerMixin.
        vhaccelerator$state = enabled ? 2 : 1;
        return enabled;
    }
}
