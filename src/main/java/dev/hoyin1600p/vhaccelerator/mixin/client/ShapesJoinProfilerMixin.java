package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.shape.ShapeJoinProfiler;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Shapes.class)
public abstract class ShapesJoinProfilerMixin {
    @Unique
    private static final ThreadLocal<ShapeJoinProfiler.Sample>
            vhaccelerator$sample = new ThreadLocal<>();

    @Inject(method = "joinUnoptimized", at = @At("HEAD"))
    private static void vhaccelerator$beginShapeJoin(
            VoxelShape first,
            VoxelShape second,
            BooleanOp operation,
            CallbackInfoReturnable<VoxelShape> callback
    ) {
        vhaccelerator$sample.set(
                ShapeJoinProfiler.begin(first, second, operation)
        );
    }

    @Inject(method = "joinUnoptimized", at = @At("RETURN"))
    private static void vhaccelerator$finishShapeJoin(
            VoxelShape first,
            VoxelShape second,
            BooleanOp operation,
            CallbackInfoReturnable<VoxelShape> callback
    ) {
        ShapeJoinProfiler.finish(vhaccelerator$sample.get());
        vhaccelerator$sample.remove();
    }
}
