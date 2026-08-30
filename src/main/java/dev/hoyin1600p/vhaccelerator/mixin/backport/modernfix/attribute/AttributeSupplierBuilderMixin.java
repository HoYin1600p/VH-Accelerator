/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/attribute_supplier_dedup/AttributeSupplierBuilderMixin.java
 * Upstream commit: 37dc9e60eb0f08c788caa5f49a6cb6bc9c0c8bf0
 * Earlier upstream commit: 3926f27d33ad00f8ed738c6297fa1b4652e3067c
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-29; adapted template interning and startup gating to Forge 1.18.2.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.attribute;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.entity.AttributeInstanceTemplates;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.entity.AttributeSupplierDeduplication;
import java.util.Map;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AttributeSupplier.Builder.class)
abstract class AttributeSupplierBuilderMixin {
    @Shadow
    @Final
    private Map<Attribute, AttributeInstance> builder;

    @Inject(
            method = "build",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/util/Map;)Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier;"
            )
    )
    private void vha$deduplicateTemplates(
            CallbackInfoReturnable<AttributeSupplier> callback
    ) {
        if (!AttributeSupplierDeduplication.startupWindowOpen()) {
            return;
        }
        this.builder.replaceAll((attribute, instance) ->
                AttributeInstanceTemplates.intern(instance));
    }
}
