package dev.hoyin1600p.vhaccelerator.mixin.chunkio;

import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.chunkio.PrefetchedChunkRead;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Reads chunk region data off the server thread.
 *
 * <p>Vanilla 1.18.2's {@code ChunkMap.scheduleChunkLoad} runs its whole body
 * on the main-thread executor, and the first thing that body does is
 * {@code readChunk} -> {@code IOWorker.load}, which is
 * {@code loadAsync(pos).join()}: the server thread sits idle while the IO
 * thread seeks the region file and inflates the chunk. Vanilla 1.19 moved the
 * read ahead of the main-thread stage; C2ME's 1.18 chunk-IO module does the
 * same. This mixin makes the same split in two narrow redirects and leaves
 * the vanilla method body, including its lambda, in place:</p>
 *
 * <ul>
 *   <li>In {@code scheduleChunkLoad}, the {@code CompletableFuture.supplyAsync}
 *   that queues the deserialization lambda is replaced by
 *   {@code loadAsync(pos)} followed by the same lambda on the same
 *   main-thread executor. Nothing else about the lambda changes: the
 *   {@code chunkLoad} profiler counter, {@code upgradeChunkTag},
 *   {@code ChunkSerializer.read} (Starlight's light data, Forge's
 *   {@code ChunkDataEvent.Load} and capabilities), {@code markPosition},
 *   the "missing level data" log, the {@code ReportedException} rethrow and
 *   the replaceable empty proto chunk fallback all still run on the server
 *   thread in their original order.</li>
 *   <li>In {@code readChunk}, the blocking {@code read(pos)} takes the
 *   already-completed result for that position instead of joining the IO
 *   thread again. It hands back the raw tag, or rethrows the read failure
 *   exactly as the blocking call would have, so the lambda's own
 *   {@code catch} blocks handle it. Any other caller of {@code readChunk}
 *   ({@code isExistingChunkFull} during saves) finds no prefetched result
 *   and reads synchronously as before.</li>
 * </ul>
 *
 * <p>Ordering against writes is unchanged: {@code loadAsync} runs on the IO
 * worker's single mailbox behind any store already queued for the position
 * and answers from a pending write when one exists, which is the same path
 * the blocking {@code load} used. The prefetched result is stored just
 * before the lambda runs and consumed inside it on the server thread, so
 * concurrent loads of different chunks never share it.</p>
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapAsyncReadMixin {
    @Unique
    @Nullable
    private PrefetchedChunkRead vhaccelerator$prefetchedRead;

    @Redirect(
            method = "scheduleChunkLoad",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;supplyAsync("
                            + "Ljava/util/function/Supplier;"
                            + "Ljava/util/concurrent/Executor;"
                            + ")Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private <T> CompletableFuture<T> vhaccelerator$readBeforeMainThread(
            Supplier<T> deserialize,
            Executor mainThreadExecutor,
            ChunkPos pos
    ) {
        if (!VHAcceleratorConfig.commonOptimizationsEnabled()) {
            return CompletableFuture.supplyAsync(deserialize, mainThreadExecutor);
        }
        IOWorker worker = ((ChunkStorageAccessor) this).vhaccelerator$getWorker();
        return ((IOWorkerInvoker) worker).vhaccelerator$loadAsync(pos)
                .handle((tag, failure) -> new PrefetchedChunkRead(pos, tag, failure))
                .thenApplyAsync(read -> {
                    vhaccelerator$prefetchedRead = read;
                    try {
                        return deserialize.get();
                    } finally {
                        vhaccelerator$prefetchedRead = null;
                    }
                }, mainThreadExecutor);
    }

    @Redirect(
            method = "readChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;read("
                            + "Lnet/minecraft/world/level/ChunkPos;"
                            + ")Lnet/minecraft/nbt/CompoundTag;"
            )
    )
    @Nullable
    private CompoundTag vhaccelerator$takePrefetchedRead(ChunkMap self, ChunkPos pos)
            throws IOException {
        PrefetchedChunkRead read = vhaccelerator$prefetchedRead;
        if (read != null && read.isFor(pos)) {
            vhaccelerator$prefetchedRead = null;
            return read.tagOrThrow();
        }
        return self.read(pos);
    }
}
