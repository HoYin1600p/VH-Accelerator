/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/worldgen_allocation/SequenceRuleMixin.java
 * Upstream commit: 2193aa11a408251b7b5b5e03ecfadc3d166c291c
 * Original copyright: Copyright (c) 2024 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted the package-private Minecraft 1.18.2 sequence record.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.worldgen;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(
        targets = "net.minecraft.world.level.levelgen.SurfaceRules$SequenceRule",
        priority = 500
)
public abstract class SequenceRuleMixin {
    @Shadow
    @Final
    private List<SurfaceRules.SurfaceRule> rules;

    /**
     * Uses indexed access while preserving the first successful surface-rule
     * result and vanilla evaluation order.
     *
     * @author embeddedt, HoYin1600p
     * @reason Avoid a list iterator allocation for each evaluated surface position.
     */
    @Overwrite
    @Nullable
    public BlockState tryApply(int x, int y, int z) {
        int size = this.rules.size();
        for (int index = 0; index < size; index++) {
            BlockState state = this.rules.get(index).tryApply(x, y, z);
            if (state != null) {
                return state;
            }
        }
        return null;
    }
}
