/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/model_optimizations/BooleanPropertyMixin.java
 * Upstream commit: fe855f15304ed788122a27cda4c2495a78374528
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; isolated exact immutable BooleanProperty set fast path.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.model.property;

import com.google.common.collect.ImmutableSet;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BooleanProperty.class)
public abstract class BooleanPropertyMixin {
    @Redirect(
            method = "equals",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableSet;"
                            + "equals(Ljava/lang/Object;)Z",
                    remap = false
            )
    )
    private boolean vha$skipConstantValueSetComparison(
            ImmutableSet<?> values,
            Object other
    ) {
        return true;
    }
}
