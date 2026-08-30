/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/attribute_supplier_dedup/AttributeSupplierMixin.java
 * Upstream commit: 653a477060f4a26a9e7442c5bf583160eea09060
 * Earlier upstream commit: 3926f27d33ad00f8ed738c6297fa1b4652e3067c
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-29; adapted the compact private-map replacement to Minecraft 1.18.2.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.attribute;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AttributeSupplier.class)
abstract class AttributeSupplierMixin {
    @Shadow
    @Final
    @Mutable
    private Map<Attribute, AttributeInstance> instances;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void vha$useCompactMap(
            Map<Attribute, AttributeInstance> instances,
            CallbackInfo callback
    ) {
        this.instances = new Object2ObjectOpenHashMap<>(this.instances);
    }
}
