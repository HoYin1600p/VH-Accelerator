/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/cache_strongholds/ConcentricRingsStructurePlacementMixin.java
 * Upstream commit: b62eb1845b978200d3f51494d08c9fb3f10c4854 (introduction)
 *                  76e0b7fc837c66f0b6c51e912dc4c7c3ec86d637 (inner-bound edge fix)
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; retargeted to Minecraft 1.18.2's record and
 * isFeatureChunk, kept only the rejection (no ring cache), and computes the
 * bounds outside the record instead of adding fields to it.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.structure.rings;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.structure.ConcentricRingBounds;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rejects chunks that lie outside the radial band any ring position of this
 * placement can reach before {@code isFeatureChunk} joins the ring
 * computation.
 *
 * <p>In 1.18.2 {@code isFeatureChunk} calls
 * {@code ChunkGenerator#getRingPositionsFor}, which blocks on the
 * asynchronous "placement calculation" future the first time a world (or
 * dimension) asks, and structure checks ask for chunks around the spawn
 * region long before that future completes. Chunks that cannot hold a
 * stronghold are now answered {@code false} without waiting; chunks inside
 * the band keep the vanilla list lookup. The bounds and their derivation are
 * documented in {@link ConcentricRingBounds}.
 */
@Mixin(ConcentricRingsStructurePlacement.class)
public abstract class ConcentricRingsStructurePlacementMixin {
    @Shadow
    @Final
    private int distance;

    @Shadow
    @Final
    private int spread;

    @Shadow
    @Final
    private int count;

    /**
     * @author embeddedt, HoYin1600p
     * @reason Avoid joining the ring computation for chunks that no ring
     * position can reach.
     */
    @Inject(method = "isFeatureChunk", at = @At("HEAD"), cancellable = true)
    private void vha$rejectChunksOutsideTheRings(
            ChunkGenerator generator,
            long seed,
            int chunkX,
            int chunkZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!ConcentricRingBounds.cached(this.distance, this.spread, this.count)
                .mayContain(chunkX, chunkZ)) {
            cir.setReturnValue(false);
        }
    }
}
