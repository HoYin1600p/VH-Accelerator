/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/worldgen_allocation/SurfaceRulesMixin.java
 * Upstream commit: 25976f3b870313502afe20913478090b9faf2f89
 * Original copyright: Copyright (c) 2024 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted the exact generated Minecraft 1.18.2 Y-condition classes.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.worldgen;

import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(
        targets = {
                "net/minecraft/world/level/levelgen/SurfaceRules$BiomeConditionSource$1BiomeCondition",
                "net/minecraft/world/level/levelgen/SurfaceRules$StoneDepthCheck$1StoneDepthCondition",
                "net/minecraft/world/level/levelgen/SurfaceRules$VerticalGradientConditionSource$1VerticalGradientCondition",
                "net/minecraft/world/level/levelgen/SurfaceRules$WaterConditionSource$1WaterCondition",
                "net/minecraft/world/level/levelgen/SurfaceRules$YConditionSource$1YCondition"
        },
        priority = 500
)
public abstract class SurfaceRulesDirectYConditionMixin extends SurfaceRules.LazyCondition {
    protected SurfaceRulesDirectYConditionMixin(SurfaceRules.Context context) {
        super(context);
    }

    /**
     * These generated conditions are evaluated for a single block position at
     * a time. Their inherited cache key changes before the next position and
     * each rule owns a distinct condition instance, so the cache bookkeeping
     * cannot produce a reusable result.
     *
     * @author VoidsongDragonfly, HoYin1600p
     * @reason Evaluate directly without the ineffective per-position cache.
     */
    @Override
    public boolean test() {
        return this.compute();
    }
}
