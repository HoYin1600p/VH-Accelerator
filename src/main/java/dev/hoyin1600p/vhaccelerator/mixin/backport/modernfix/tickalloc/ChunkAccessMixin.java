/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted from ModernFix's ticking-chunk allocation reductions.
 * See THIRD_PARTY_NOTICES.md and docs/MODERNFIX_BACKPORTS.md.
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
