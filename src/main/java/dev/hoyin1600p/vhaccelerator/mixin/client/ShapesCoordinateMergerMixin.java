package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.shape.FastCoordinateMerger;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.phys.shapes.IndexMerger;
import net.minecraft.world.phys.shapes.Shapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Shapes.class)
public abstract class ShapesCoordinateMergerMixin {
    @Inject(
            method = "createIndexMerger",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/world/phys/shapes/IndirectMerger"
            ),
            cancellable = true
    )
    private static void vhaccelerator$useFlatCoordinateMerger(
            int size,
            DoubleList first,
            DoubleList second,
            boolean includeFirstOnly,
            boolean includeSecondOnly,
            CallbackInfoReturnable<IndexMerger> callback
    ) {
        if (VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .optimizeVoxelShapeMerging
                )) {
            callback.setReturnValue(new FastCoordinateMerger(
                    first,
                    second,
                    includeFirstOnly,
                    includeSecondOnly
            ));
        }
    }
}
