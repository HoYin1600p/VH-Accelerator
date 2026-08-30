/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/mojang_registry_size/ResourceKeyMixin.java
 * Upstream commit: fe855f15304ed788122a27cda4c2495a78374528
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; added a constructor invoker for private 1.18 ResourceKey creation.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.registry.resourcekey;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ResourceKey.class)
public interface ResourceKeyConstructor {
    @Invoker("<init>")
    static <T> ResourceKey<T> vha$construct(
            ResourceLocation registry,
            ResourceLocation location
    ) {
        throw new AssertionError("mixin constructor invoker was not applied");
    }
}
