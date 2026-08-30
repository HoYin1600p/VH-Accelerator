/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/mojang_registry_size/ResourceKeyMixin.java
 * Upstream commit: fe855f15304ed788122a27cda4c2495a78374528
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted two-level interning to the private 1.18 factory.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.registry.resourcekey;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.registry.ResourceKeyCache;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResourceKey.class)
public abstract class ResourceKeyMixin<T> {
    @Inject(
            method = "create(Lnet/minecraft/resources/ResourceLocation;"
                    + "Lnet/minecraft/resources/ResourceLocation;)"
                    + "Lnet/minecraft/resources/ResourceKey;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static <T> void vha$createWithoutCompositeString(
            ResourceLocation registry,
            ResourceLocation location,
            CallbackInfoReturnable<ResourceKey<T>> callback
    ) {
        callback.setReturnValue(ResourceKeyCache.getOrCreate(
                registry,
                location
        ));
    }
}
