package dev.hoyin1600p.vhaccelerator.mixin.shape;

import dev.hoyin1600p.vhaccelerator.shape.FastCoordinateMerger;
import dev.hoyin1600p.vhaccelerator.shape.ShapeLaunchPolicy;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.phys.shapes.IndexMerger;
import net.minecraft.world.phys.shapes.Shapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Shapes.class)
public abstract class ShapesCoordinateMergerMixin {
    @Unique
    private static volatile int vhaccelerator$state;

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
        if (vhaccelerator$enabled()) {
            callback.setReturnValue(new FastCoordinateMerger(
                    first,
                    second,
                    includeFirstOnly,
                    includeSecondOnly
            ));
        }
    }

    @Unique
    private static boolean vhaccelerator$enabled() {
        int cached = vhaccelerator$state;
        if (cached != 0) {
            return cached == 2;
        }
        int decision = ShapeLaunchPolicy.decide(ShapeLaunchPolicy.Feature.MERGER);
        if (decision == ShapeLaunchPolicy.UNDECIDED) {
            return false;
        }

        boolean enabled = decision == ShapeLaunchPolicy.ON;
        // Zero is deliberately the uncaptured state. Mixin can merge fields
        // after the target's own static initializer, so this decision must be
        // safe before any Mixin-added initializer could have run.
        vhaccelerator$state = enabled ? 2 : 1;
        return enabled;
    }
}
