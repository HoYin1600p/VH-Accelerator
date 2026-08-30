/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_structure_location/StructureCheckMixin.java
 * Upstream commit: 2e8c00357239827b05c9afd06f6a000e524ac3d9
 * Original copyright: Copyright (c) 2024 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted to Minecraft 1.18.2 configured structures and standard Mixin injection.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.structure;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(StructureCheck.class)
public abstract class StructureCheckMixin {
    @Shadow
    @Final
    private ChunkGenerator chunkGenerator;

    @Shadow
    @Final
    private long seed;

    @Shadow
    @Nullable
    private StructureCheckResult tryLoadFromStorage(
            ChunkPos chunkPos,
            ConfiguredStructureFeature<?, ?> structure,
            boolean skipKnownStructures,
            long packedChunkPos
    ) {
        throw new AssertionError();
    }

    /**
     * Avoids the expensive full structure-generation probe when the existing
     * generator placement table proves that the requested structure cannot
     * start in the candidate chunk.
     *
     * @author embeddedt, HoYin1600p
     * @reason Reject impossible structure-location candidates before vanilla's
     * full canGenerate check while preserving every decisive storage result.
     */
    @Redirect(
            method = "checkStart",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/structure/StructureCheck;"
                            + "tryLoadFromStorage(Lnet/minecraft/world/level/ChunkPos;"
                            + "Lnet/minecraft/world/level/levelgen/feature/ConfiguredStructureFeature;ZJ)"
                            + "Lnet/minecraft/world/level/levelgen/structure/StructureCheckResult;"
            )
    )
    private StructureCheckResult vha$rejectImpossibleStructurePosition(
            StructureCheck ignoredInstance,
            ChunkPos chunkPos,
            ConfiguredStructureFeature<?, ?> structure,
            boolean skipKnownStructures,
            long packedChunkPos
    ) {
        StructureCheckResult storageResult = this.tryLoadFromStorage(
                chunkPos,
                structure,
                skipKnownStructures,
                packedChunkPos
        );
        if (storageResult != null) {
            return storageResult;
        }

        Holder<ConfiguredStructureFeature<?, ?>> holder = Holder.direct(structure);
        for (StructurePlacement placement
                : ((ChunkGeneratorAccessor) this.chunkGenerator)
                .vha$getPlacementsForFeature(holder)) {
            if (placement.isFeatureChunk(
                    this.chunkGenerator,
                    this.seed,
                    chunkPos.x,
                    chunkPos.z
            )) {
                return null;
            }
        }
        return StructureCheckResult.START_NOT_PRESENT;
    }
}
