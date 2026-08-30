/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/mojang_registry_size/ResourceKeyMixin.java
 * Upstream commit: fe855f15304ed788122a27cda4c2495a78374528
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; moved initialized state out of the mixin target and
 * retained the two-level 1.18 key interner without composite interned strings.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.registry;

import dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.registry.resourcekey.ResourceKeyConstructor;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public final class ResourceKeyCache {
    private static final Map<
            ResourceLocation,
            Map<ResourceLocation, ResourceKey<?>>
            > KEYS = new Object2ObjectOpenHashMap<>();

    private ResourceKeyCache() {
    }

    @SuppressWarnings("unchecked")
    public static synchronized <T> ResourceKey<T> getOrCreate(
            ResourceLocation registry,
            ResourceLocation location
    ) {
        Map<ResourceLocation, ResourceKey<?>> registryKeys =
                KEYS.computeIfAbsent(
                        registry,
                        ignored -> new Object2ObjectOpenHashMap<>()
                );
        ResourceKey<?> key = registryKeys.get(location);
        if (key == null) {
            key = ResourceKeyConstructor.vha$construct(registry, location);
            registryKeys.put(location, key);
        }
        return (ResourceKey<T>) key;
    }
}
