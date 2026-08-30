/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/worldgen_allocation/ClimateParameterListMixin.java
 * Upstream commit: 4e3ec7898ed34a1eabb87d3ff39129e264434e3f
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted Minecraft 1.18.2 descriptors and retained safe volatile publication.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.worldgen;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.world.level.biome.Climate;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Defers the climate R-tree from datapack/bootstrap construction until the
 * first indexed biome lookup. This shifts unused biome-source setup out of
 * launch without changing the tree or its search algorithm.
 */
@Mixin(value = Climate.ParameterList.class, priority = 500)
public abstract class ClimateParameterListMixin<T> {
    @Shadow
    @Final
    @Mutable
    private Climate.RTree<T> index;

    @Shadow
    @Final
    private List<Pair<Climate.ParameterPoint, T>> values;

    /* Deliberately has no initializer: JVM false is safe during target <clinit>. */
    @Unique
    private volatile boolean vha$climateTreeInitialized;

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Climate$RTree;"
                            + "create(Ljava/util/List;)Lnet/minecraft/world/level/biome/Climate$RTree;"
            )
    )
    private Climate.RTree<T> vha$deferClimateTree(
            List<Pair<Climate.ParameterPoint, T>> nodes
    ) {
        return null;
    }

    @Redirect(
            method = "findValueIndex(Lnet/minecraft/world/level/biome/Climate$TargetPoint;"
                    + "Lnet/minecraft/world/level/biome/Climate$DistanceMetric;)Ljava/lang/Object;",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/biome/Climate$ParameterList;"
                            + "index:Lnet/minecraft/world/level/biome/Climate$RTree;",
                    opcode = Opcodes.GETFIELD
            )
    )
    private Climate.RTree<T> vha$getClimateTree(
            Climate.ParameterList<T> ignoredInstance
    ) {
        if (!this.vha$climateTreeInitialized) {
            synchronized (this) {
                if (!this.vha$climateTreeInitialized) {
                    this.index = Climate.RTree.create(this.values);
                    this.vha$climateTreeInitialized = true;
                }
            }
        }
        return this.index;
    }
}
