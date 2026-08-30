/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/mojang_registry_size/MappedRegistryMixin.java
 * Upstream commit: fe855f15304ed788122a27cda4c2495a78374528
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; isolated exact Minecraft 1.18.2 geometric backing-list growth.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.registry.growth;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MappedRegistry.class)
public abstract class MappedRegistryMixin {
    @Redirect(
            method = "registerMapping(ILnet/minecraft/resources/ResourceKey;"
                    + "Ljava/lang/Object;Lcom/mojang/serialization/Lifecycle;Z)"
                    + "Lnet/minecraft/core/Holder;",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/ObjectList;"
                            + "size(I)V",
                    remap = false
            )
    )
    private void vha$growGeometrically(ObjectList<?> list, int requestedSize) {
        if (!(list instanceof ObjectArrayList<?>)
                || requestedSize <= list.size()) {
            list.size(requestedSize);
            return;
        }

        int capacity = Integer.highestOneBit(requestedSize);
        if (capacity != requestedSize) {
            capacity <<= 1;
        }
        ((ObjectArrayList<?>) list).ensureCapacity(capacity);
        while (list.size() < requestedSize) {
            list.add(null);
        }
    }
}
