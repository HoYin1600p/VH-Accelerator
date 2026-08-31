/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/mixin/perf/potential_spawns_alloc/ForgeEventFactoryMixin.java
 * Upstream commit: 1814bd3e1faff1518119a7b5fcf85784c796023d
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: retargeted Forge 40 with independent exact
 * ModernFix ownership selection.
 * Modified: 2026-08-30
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.spawns;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ForgeEventFactory.class)
public final class ForgeEventFactoryMixin {
    @Redirect(
            method = "getPotentialSpawns",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/random/WeightedRandomList;create(Ljava/util/List;)Lnet/minecraft/util/random/WeightedRandomList;"
            )
    )
    private static WeightedRandomList<MobSpawnSettings.SpawnerData>
            vha$reuseUnchangedList(
                    List<MobSpawnSettings.SpawnerData> items,
                    LevelAccessor level,
                    MobCategory category,
                    BlockPos pos,
                    WeightedRandomList<MobSpawnSettings.SpawnerData> original
            ) {
        if (items == original.unwrap()) {
            return original;
        }
        return WeightedRandomList.create(items);
    }
}
