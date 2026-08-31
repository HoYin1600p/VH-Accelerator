/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/ticking_chunk_alloc/ChunkAccessMixin.java
 * Upstream commit: 8cca316fb521b96da9428beb0d2cb4da21ca0658
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: preserves a live view from the initially
 * empty state and delegates mutation safety to an external tested helper.
 * Modified: 2026-08-30
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.tickalloc;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.world.LiveReadOnlyMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ChunkAccess.class, priority = 800)
public abstract class ChunkAccessMixin {
    @Shadow
    @Final
    private Map<ConfiguredStructureFeature<?, ?>, LongSet>
            structuresRefences;

    @Unique
    private Map<ConfiguredStructureFeature<?, ?>, LongSet>
            vha$structureReferencesView;

    /**
     * @author embeddedt and HoYin1600p
     * @reason Cache the read-only structure-reference view and reuse JDK empty
     * collection views without losing vanilla's live-view behavior.
     */
    @Overwrite
    public Map<ConfiguredStructureFeature<?, ?>, LongSet> getAllReferences() {
        Map<ConfiguredStructureFeature<?, ?>, LongSet> view =
                vha$structureReferencesView;
        if (view == null) {
            view = new LiveReadOnlyMap<>(structuresRefences);
            vha$structureReferencesView = view;
        }
        return view;
    }
}
