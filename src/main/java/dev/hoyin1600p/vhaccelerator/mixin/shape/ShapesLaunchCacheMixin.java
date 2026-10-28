package dev.hoyin1600p.vhaccelerator.mixin.shape;

import dev.hoyin1600p.vhaccelerator.shape.LaunchShapeCache;
import dev.hoyin1600p.vhaccelerator.shape.LeafShapes;
import dev.hoyin1600p.vhaccelerator.shape.ShapeLaunchPolicy;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reuses identical joins while mods build their block shapes during launch.
 * See {@link LaunchShapeCache}. The {@code Shapes.box} sharing lives in
 * {@link ShapesBoxCacheMixin}; without it (Canary) equal cuboid leaves are
 * matched by content in {@link LeafShapes}.
 */
@Mixin(Shapes.class)
public abstract class ShapesLaunchCacheMixin {
    @Unique
    private static volatile int vhaccelerator$state;

    @Inject(method = "joinUnoptimized", at = @At("HEAD"), cancellable = true)
    private static void vhaccelerator$reuseJoin(
            VoxelShape first,
            VoxelShape second,
            BooleanOp operation,
            CallbackInfoReturnable<VoxelShape> callback
    ) {
        if (vhaccelerator$enabled()) {
            Object joined = LaunchShapeCache.join(
                    LeafShapes.canonical(first), LeafShapes.canonical(second), operation, first == second);
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
            LaunchShapeCache.storeJoin(
                    LeafShapes.canonical(first), LeafShapes.canonical(second), operation, first == second,
                    callback.getReturnValue());
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
