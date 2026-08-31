/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted from ModernFix's wall-shape deduplication implementation.
 * See THIRD_PARTY_NOTICES.md and docs/MODERNFIX_BACKPORTS.md.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.wallshape;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.world.WallShapeCache;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WallBlock.class)
public abstract class WallBlockMixin extends Block {
    protected WallBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "makeShapes", at = @At("HEAD"), cancellable = true)
    private void vha$reuseShapes(
            float f1,
            float f2,
            float f3,
            float f4,
            float f5,
            float f6,
            CallbackInfoReturnable<Map<BlockState, VoxelShape>> cir
    ) {
        Map<BlockState, VoxelShape> cached = WallShapeCache.reuse(
                this.stateDefinition,
                f1,
                f2,
                f3,
                f4,
                f5,
                f6
        );
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "makeShapes", at = @At("RETURN"))
    private void vha$rememberVanillaShapes(
            float f1,
            float f2,
            float f3,
            float f4,
            float f5,
            float f6,
            CallbackInfoReturnable<Map<BlockState, VoxelShape>> cir
    ) {
        // A subclass may override behavior or construct shapes with additional
        // state. It may consume a known cache, but it must never seed one.
        if ((Class<?>) this.getClass() != WallBlock.class) {
            return;
        }
        WallShapeCache.remember(
                this.stateDefinition,
                cir.getReturnValue(),
                f1,
                f2,
                f3,
                f4,
                f5,
                f6
        );
    }
}
