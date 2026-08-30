/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/bugfix/buffer_builder_leak/RenderBuffersMixin.java
 * Upstream commit: d51b0f60a23b167b6ee8459073c706ab8b20a6fe
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-29; extracted the duplicate check and adapted it to Forge 1.18.2.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.buffer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.render.DuplicateBufferBuilderGuard;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderBuffers.class)
abstract class RenderBuffersMixin {
    @Inject(method = "put", at = @At("HEAD"), cancellable = true)
    private static void vha$preventDuplicateBufferAllocation(
            Object2ObjectLinkedOpenHashMap<RenderType, BufferBuilder> builders,
            RenderType renderType,
            CallbackInfo callback
    ) {
        if (!DuplicateBufferBuilderGuard.shouldCreate(builders, renderType)) {
            callback.cancel();
        }
    }
}
