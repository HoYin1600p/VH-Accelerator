/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/chunk_meshing/RebuildTaskMixin.java
 * Upstream commit: 7c550a1ce485f4a253f09447cddebf0e6839e554
 * Original copyright: Copyright (c) 2024 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-29; replaced MixinExtras local capture with guarded per-task reuse.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.render;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.render.SectionBlockPosIterator;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(
        targets = "net.minecraft.client.renderer.chunk."
                + "ChunkRenderDispatcher$RenderChunk$RebuildTask",
        priority = 2000
)
abstract class RebuildTaskMixin {
    @Unique
    private BlockState vha$capturedBlockState;

    @Unique
    private long vha$capturedBlockPosition;

    @Redirect(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;betweenClosed("
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"
            ),
            require = 0
    )
    private Iterable<BlockPos> vha$useSectionIterator(
            BlockPos firstPosition,
            BlockPos secondPosition
    ) {
        return SectionBlockPosIterator.betweenClosed(
                firstPosition,
                secondPosition
        );
    }

    @Redirect(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/"
                            + "RenderChunkRegion;getBlockState("
                            + "Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/world/level/block/state/BlockState;",
                    ordinal = 0
            ),
            require = 0
    )
    private BlockState vha$captureBlockState(
            RenderChunkRegion region,
            BlockPos position
    ) {
        BlockState state = region.getBlockState(position);
        vha$capturedBlockPosition = position.asLong();
        vha$capturedBlockState = state;
        return state;
    }

    @Redirect(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/"
                            + "RenderChunkRegion;getBlockState("
                            + "Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/world/level/block/state/BlockState;",
                    ordinal = 1
            ),
            require = 0
    )
    private BlockState vha$reuseBlockState(
            RenderChunkRegion region,
            BlockPos position
    ) {
        BlockState state = vha$capturedBlockState;
        if (state != null && vha$capturedBlockPosition == position.asLong()) {
            return state;
        }
        return region.getBlockState(position);
    }
}
