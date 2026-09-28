/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/deduplicate_location/MixinResourceLocation.java
 * Upstream commit: 64eb01987f8c15865858a10b5ef096685b5caf00
 * Original copyright: Copyright (c) 2022-2023 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; deduplicates only the namespace, after validation,
 * through VHA's bounded lock-free table and ownership gate.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.registry.location;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.registry.ResourceLocationNamespaces;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResourceLocation.class)
public abstract class ResourceLocationNamespaceMixin {
    @Shadow
    @Mutable
    @Final
    protected String namespace;

    /** Every constructor delegates here; RETURN runs after validation. */
    @Inject(method = "<init>([Ljava/lang/String;)V", at = @At("RETURN"))
    private void vha$deduplicateNamespace(String[] parts, CallbackInfo callback) {
        this.namespace = ResourceLocationNamespaces.canonical(this.namespace);
    }
}
