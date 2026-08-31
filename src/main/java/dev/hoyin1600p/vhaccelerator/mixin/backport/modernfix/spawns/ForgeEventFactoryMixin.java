/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted from ModernFix's Forge potential-spawn allocation reduction.
 * See THIRD_PARTY_NOTICES.md and docs/MODERNFIX_BACKPORTS.md.
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
