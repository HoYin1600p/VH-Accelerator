/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/ticking_chunk_alloc/ChunkHolderMixin.java
 * Upstream commit: 070b7b6d126af5d04cb6a4315d823a2ad6dbc6bb
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: delegates Either extraction to a fail-safe
 * helper and claims only the complete exact ModernFix option group.
 * Modified: 2026-08-30
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.tickalloc;

import com.mojang.datafixers.util.Either;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.world.EitherValueAccess;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ChunkHolder.class, priority = 500)
public abstract class ChunkHolderMixin {
    @Shadow
    public abstract CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>>
            getTickingChunkFuture();

    @Shadow
    public abstract CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>>
            getFullChunkFuture();

    /**
     * @author embeddedt and HoYin1600p
     * @reason Avoid a transient Optional in the active chunk-tick path.
     */
    @Overwrite
    public LevelChunk getTickingChunk() {
        Either<LevelChunk, ChunkHolder.ChunkLoadingFailure> value =
                getTickingChunkFuture().getNow(null);
        return value == null ? null : EitherValueAccess.leftOrNull(value);
    }

    /**
     * @author embeddedt and HoYin1600p
     * @reason Avoid a transient Optional in the active chunk-tick path.
     */
    @Overwrite
    public LevelChunk getFullChunk() {
        Either<LevelChunk, ChunkHolder.ChunkLoadingFailure> value =
                getFullChunkFuture().getNow(null);
        return value == null ? null : EitherValueAccess.leftOrNull(value);
    }
}
