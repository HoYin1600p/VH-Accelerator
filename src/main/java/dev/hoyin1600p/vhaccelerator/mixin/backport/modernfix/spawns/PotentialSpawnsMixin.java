/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/mixin/perf/potential_spawns_alloc/PotentialSpawnsMixin.java
 * Upstream commit: 1814bd3e1faff1518119a7b5fcf85784c796023d
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: retargeted Forge 40 and moved sentinel state
 * to an external holder while preserving copy-on-write event behavior.
 * Modified: 2026-08-30
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.spawns;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.world.PotentialSpawnListSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.event.world.WorldEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldEvent.PotentialSpawns.class)
public final class PotentialSpawnsMixin {
    @Shadow(remap = false)
    @Final
    @Mutable
    private List<MobSpawnSettings.SpawnerData> view;

    @Shadow(remap = false)
    @Final
    @Mutable
    private List<MobSpawnSettings.SpawnerData> list;

    @Redirect(
            method = "<init>",
            at = @At(value = "NEW", target = "java/util/ArrayList", ordinal = 1)
    )
    private ArrayList<?> vha$avoidEmptyCopy() {
        return PotentialSpawnListSupport.sentinel();
    }

    @Redirect(
            method = "<init>",
            at = @At(value = "NEW", target = "java/util/ArrayList", ordinal = 0)
    )
    private ArrayList<?> vha$avoidPopulatedCopy(Collection<?> source) {
        return PotentialSpawnListSupport.sentinel();
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Collections;unmodifiableList(Ljava/util/List;)Ljava/util/List;"
            )
    )
    private List<?> vha$avoidUnusedView(List<?> source) {
        return null;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void vha$initializeCopyOnWriteLists(
            LevelAccessor level,
            MobCategory category,
            BlockPos pos,
            WeightedRandomList<MobSpawnSettings.SpawnerData> oldList,
            CallbackInfo ci
    ) {
        view = oldList.unwrap();
        list = null;
    }

    @Inject(method = "addSpawnerData", at = @At("HEAD"), remap = false)
    private void vha$copyBeforeAdd(
            MobSpawnSettings.SpawnerData data,
            CallbackInfo ci
    ) {
        vha$ensureMutable();
    }

    @Inject(method = "removeSpawnerData", at = @At("HEAD"), remap = false)
    private void vha$copyBeforeRemove(
            MobSpawnSettings.SpawnerData data,
            CallbackInfoReturnable<Boolean> cir
    ) {
        vha$ensureMutable();
    }

    private void vha$ensureMutable() {
        if (list != null) {
            return;
        }
        list = new ArrayList<>(view);
        view = Collections.unmodifiableList(list);
    }
}
