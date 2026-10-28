package dev.hoyin1600p.vhaccelerator.mixin.shape;

import dev.hoyin1600p.vhaccelerator.shape.LaunchShapeCache;
import dev.hoyin1600p.vhaccelerator.shape.ShapeLaunchPolicy;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shares equal {@code Shapes.box} bounds while mods build their block shapes
 * during launch. Kept apart from the join hooks in
 * {@link ShapesLaunchCacheMixin} because Canary replaces {@code Shapes.box}
 * outright ({@code @Overwrite}). Beside Canary the injectors wrap Canary's
 * specialised boxes, which only works when this mixin is applied after
 * Canary's overwrite; the higher priority (default 1000) does that. If the
 * hooks do not run, {@link dev.hoyin1600p.vhaccelerator.shape.LeafShapes}
 * matches Canary's cuboids by content instead. Left out with Lithium.
 */
@Mixin(value = Shapes.class, priority = 1500)
public abstract class ShapesBoxCacheMixin {
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

    @Unique
    private static boolean vhaccelerator$enabled() {
        if (!LaunchShapeCache.isOpen()) {
            return false;
        }
        int cached = vhaccelerator$state;
        if (cached != 0) {
            return cached == 2;
        }
        int decision = ShapeLaunchPolicy.decide(ShapeLaunchPolicy.Feature.CACHE);
        if (decision == ShapeLaunchPolicy.UNDECIDED) {
            return false;
        }
        boolean enabled = decision == ShapeLaunchPolicy.ON;
        // Zero is the uncaptured state; see ShapesCoordinateMergerMixin.
        vhaccelerator$state = enabled ? 2 : 1;
        return enabled;
    }
}
