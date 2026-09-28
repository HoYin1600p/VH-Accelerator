package dev.hoyin1600p.vhaccelerator.mixin.chunkio;

import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the region-file worker that {@code ChunkMap} inherits from {@code ChunkStorage}. */
@Mixin(ChunkStorage.class)
public interface ChunkStorageAccessor {
    @Accessor("worker")
    IOWorker vhaccelerator$getWorker();
}
