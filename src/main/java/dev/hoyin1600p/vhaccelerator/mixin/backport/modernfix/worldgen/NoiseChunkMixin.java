/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/worldgen_allocation/NoiseChunkMixin.java
 * Upstream commit: 2193aa11a408251b7b5b5e03ecfadc3d166c291c
 * Original copyright: Copyright (c) 2024 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retained the vanilla 1.18.2 map and replaced only the hot computeIfAbsent path.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.worldgen;

import java.util.Map;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = NoiseChunk.class, priority = 500)
public abstract class NoiseChunkMixin {
    @Shadow
    @Final
    private Map<DensityFunction, DensityFunction> wrapped;

    @Shadow
    protected abstract DensityFunction wrapNew(DensityFunction function);

    /**
     * Performs the same cache lookup without allocating a bound method-reference
     * object for each uncached density function.
     *
     * @author embeddedt, HoYin1600p
     * @reason Avoid computeIfAbsent's mapping-function allocation in world generation.
     */
    @Overwrite
    protected DensityFunction wrap(DensityFunction unwrapped) {
        DensityFunction wrappedFunction = this.wrapped.get(unwrapped);
        if (wrappedFunction == null) {
            wrappedFunction = this.wrapNew(unwrapped);
            if (wrappedFunction != null) {
                this.wrapped.put(unwrapped, wrappedFunction);
            }
        }
        return wrappedFunction;
    }
}
