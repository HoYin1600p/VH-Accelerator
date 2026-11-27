/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_biome_lookups/ServerLevelMixin.java
 * Upstream commit: 20e048f9e33d961032a9308f6ae7c7a8a1e2573a
 * Original copyright: Copyright (c) 2026; embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-11-27; retargeted getNoiseBiome loaded-FULL-chunk fast path to Minecraft 1.18.2.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.biome;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerLevel.class)
public abstract class ServerLevelBiomeLookupMixin implements LevelReader {
    @Shadow
    public abstract ServerChunkCache getChunkSource();

    // ServerChunkCache's status cache matches exact ChunkStatus, so BIOMES queries miss loaded FULL chunks.
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        int chunkX = QuartPos.toSection(x);
        int chunkZ = QuartPos.toSection(z);
        LevelChunk loaded = this.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (loaded != null) {
            return loaded.getNoiseBiome(x, y, z);
        }
        ChunkAccess chunk = this.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.BIOMES, false);
        return chunk != null ? chunk.getNoiseBiome(x, y, z) : this.getUncachedNoiseBiome(x, y, z);
    }
}
