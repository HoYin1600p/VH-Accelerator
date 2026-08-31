/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/mixin/bugfix/ctm_resourceutil_cme/ResourceUtilMixin.java
 * Upstream commit: 5de87576ca17b920e88f9c4fc289f3df064ef694
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: made CTM an optional string target and gated
 * the mixin to VHA's validated CTM 1.18.2 layout.
 * Modified: 2026-08-30
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.ctm;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Makes CTM's nullable metadata cache safe during parallel model loading. */
@Pseudo
@Mixin(targets = "team.chisel.ctm.client.util.ResourceUtil", remap = false)
@SuppressWarnings({"rawtypes", "unchecked"})
public final class ResourceUtilMixin {
    @Shadow(remap = false)
    @Final
    @Mutable
    private static Map metadataCache;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void vha$synchronizeMetadataCache(CallbackInfo ci) {
        // A ConcurrentHashMap cannot replace this map because CTM deliberately
        // caches null for textures without CTM metadata.
        if (!(metadataCache instanceof ConcurrentMap)) {
            metadataCache = Collections.synchronizedMap(metadataCache);
        }
    }
}
