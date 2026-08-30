/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/state_definition_construct/StateDefinitionMixin.java
 * Upstream commit: 49464451ddc6b174740b2fd14057611441d26f40
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted to Forge 40 and placed under VHA's exact-option ownership gate.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.blockstate.definition;

import com.google.common.collect.ImmutableSortedMap;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.blockstate.GracefulStateMap;
import java.util.Map;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(StateDefinition.class)
public abstract class StateDefinitionMixin<O, S extends StateHolder<O, S>> {
    @Shadow
    @Final
    private ImmutableSortedMap<String, Property<?>> propertiesByName;

    @ModifyVariable(
            method = "<init>",
            at = @At(value = "STORE", ordinal = 0),
            ordinal = 1,
            index = 8,
            require = 1
    )
    private Map<Map<Property<?>, Comparable<?>>, S> vha$useArrayFirstMap(
            Map<Map<Property<?>, Comparable<?>>, S> original
    ) {
        int stateCount = 1;
        for (Property<?> property : propertiesByName.values()) {
            stateCount = Math.multiplyExact(
                    stateCount,
                    property.getPossibleValues().size()
            );
        }
        return new GracefulStateMap<>(stateCount);
    }
}
