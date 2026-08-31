/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/deduplicate_wall_shapes/WallBlockMixin.java
 * Upstream commit: 94c848b0debbb5291ab3c709353e3f11613fd14d
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: delegated static cache state to a normal
 * holder class and added independent ownership selection.
 * Modified: 2026-08-30
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
