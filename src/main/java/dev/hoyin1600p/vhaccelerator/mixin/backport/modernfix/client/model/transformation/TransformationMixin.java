/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/model_optimizations/TransformationMatrixMixin.java
 * Upstream commit: fe855f15304ed788122a27cda4c2495a78374528
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; replaced boxed cache state with allocation-free primitive state.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.model.transformation;

import com.mojang.math.Matrix4f;
import com.mojang.math.Transformation;
import java.util.Objects;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Transformation.class)
public abstract class TransformationMixin {
    @Shadow
    @Final
    private Matrix4f matrix;

    @Unique
    private int vha$cachedHashCode;

    @Unique
    private boolean vha$hashCodeComputed;

    /**
     * @author embeddedt, HoYin1600p
     * @reason Transform matrices are immutable after construction and are
     * repeatedly used as model-cache keys during baking.
     */
    @Overwrite(remap = false)
    public int hashCode() {
        if (!this.vha$hashCodeComputed) {
            this.vha$cachedHashCode = Objects.hashCode(this.matrix);
            this.vha$hashCodeComputed = true;
        }
        return this.vha$cachedHashCode;
    }
}
