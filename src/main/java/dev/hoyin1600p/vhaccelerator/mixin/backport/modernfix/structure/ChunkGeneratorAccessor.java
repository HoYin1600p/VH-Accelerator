/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_structure_location/ChunkGeneratorAccessor.java
 * Upstream commit: 2e8c00357239827b05c9afd06f6a000e524ac3d9
 * Original copyright: Copyright (c) 2024 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted to Minecraft 1.18.2 configured structures.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.structure;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorAccessor {
    @Invoker("getPlacementsForFeature")
    List<StructurePlacement> vha$getPlacementsForFeature(
            Holder<ConfiguredStructureFeature<?, ?>> structure
    );
}
