/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/worldgen_allocation/MaterialRuleListMixin.java
 * Upstream commit: 2193aa11a408251b7b5b5e03ecfadc3d166c291c
 * Original copyright: Copyright (c) 2024 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted and documented for Minecraft Forge 1.18.2.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.worldgen;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = MaterialRuleList.class, priority = 500)
public abstract class MaterialRuleListMixin {
    @Shadow
    @Final
    private List<NoiseChunk.BlockStateFiller> materialRuleList;

    /**
     * Uses indexed access so the material-selection hot path does not allocate
     * a new list iterator for every density-function position.
     *
     * @author embeddedt, HoYin1600p
     * @reason Avoid a semantically unnecessary iterator allocation.
     */
    @Overwrite
    @Nullable
    public BlockState calculate(DensityFunction.FunctionContext context) {
        int size = this.materialRuleList.size();
        for (int index = 0; index < size; index++) {
            BlockState state = this.materialRuleList.get(index).calculate(context);
            if (state != null) {
                return state;
            }
        }
        return null;
    }
}
