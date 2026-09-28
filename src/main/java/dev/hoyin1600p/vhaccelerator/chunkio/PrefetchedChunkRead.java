package dev.hoyin1600p.vhaccelerator.chunkio;

import java.io.IOException;
import java.util.concurrent.CompletionException;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

/**
 * The outcome of one region-file read that finished on the chunk IO thread
 * and is handed to the server thread's chunk deserialization.
 *
 * <p>{@link #tagOrThrow()} reproduces what vanilla's blocking
 * {@code IOWorker.load} would have returned or thrown for the same read:
 * the raw tag (or {@code null} for an absent chunk), an {@link IOException}
 * unwrapped from the completion, or a {@link CompletionException} for any
 * other failure. That keeps {@code ChunkMap.scheduleChunkLoad}'s own
 * error handling (log, mark replaceable, empty proto chunk) unchanged.</p>
 */
public final class PrefetchedChunkRead {
    private final ChunkPos pos;
    @Nullable
    private final CompoundTag tag;
    @Nullable
    private final Throwable failure;

    public PrefetchedChunkRead(ChunkPos pos, @Nullable CompoundTag tag, @Nullable Throwable failure) {
        this.pos = pos;
        this.tag = tag;
        this.failure = failure;
    }

    public boolean isFor(ChunkPos other) {
        return pos.equals(other);
    }

    @Nullable
    public CompoundTag tagOrThrow() throws IOException {
        if (failure == null) {
            return tag;
        }
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        if (cause instanceof IOException io) {
            throw io;
        }
        throw failure instanceof CompletionException completion
                ? completion
                : new CompletionException(failure);
    }
}
