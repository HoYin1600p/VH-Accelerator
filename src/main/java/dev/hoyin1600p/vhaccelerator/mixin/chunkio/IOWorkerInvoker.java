package dev.hoyin1600p.vhaccelerator.mixin.chunkio;

import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code IOWorker.loadAsync}, the non-blocking half of the blocking
 * {@code IOWorker.load} that vanilla calls on the server thread. It runs on
 * the worker's own mailbox, after any store already queued for the same
 * position, and answers from a pending write when one exists.
 */
@Mixin(IOWorker.class)
public interface IOWorkerInvoker {
    @Invoker("loadAsync")
    CompletableFuture<CompoundTag> vhaccelerator$loadAsync(ChunkPos pos);
}
