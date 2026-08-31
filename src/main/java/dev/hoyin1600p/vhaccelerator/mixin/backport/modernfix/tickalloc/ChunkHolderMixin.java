/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted from ModernFix's ticking-chunk allocation reductions.
 * See THIRD_PARTY_NOTICES.md and docs/MODERNFIX_BACKPORTS.md.
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
