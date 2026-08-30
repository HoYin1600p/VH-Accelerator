/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/model_optimizations/PropertyMixin.java
 * Upstream commit: fe855f15304ed788122a27cda4c2495a78374528
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; isolated 1.18 property-name interning behind VHA ownership.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.model.property;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.model.PropertyNameCache;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Property.class)
public abstract class PropertyMixin<T extends Comparable<T>> {
    @Shadow
    @Mutable
    @Final
    private String name;

    @Shadow
    @Final
    private Class<T> clazz;

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/block/state/"
                            + "properties/Property;name:Ljava/lang/String;"
            )
    )
    private void vha$deduplicateName(
            Property<?> property,
            String value
    ) {
        this.name = PropertyNameCache.deduplicate(value);
    }

    /**
     * @author embeddedt
     * @reason Property names are canonical while this option is active, so
     * reference comparison avoids repeated String content comparisons.
     */
    @Overwrite(remap = false)
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Property<?> property)) {
            return false;
        }
        return this.clazz == property.getValueClass()
                && this.name == property.getName();
    }
}
