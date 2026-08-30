/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/bugfix/concurrency/MappedRegistryMixin.java
 * Upstream commit: 873e3bd67654c0efe8a24237eeb90dea94a3de77
 * Original copyright: Copyright (c) 2025 embeddedt, Uncandango, and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; ported double-checked tag creation to Minecraft 1.18.2.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.tag;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = MappedRegistry.class, priority = 500)
public abstract class MappedRegistryTagMixin<T> {
    @Shadow
    private volatile Map<TagKey<T>, HolderSet.Named<T>> tags;

    @Shadow
    private HolderSet.Named<T> createTag(TagKey<T> key) {
        throw new AssertionError();
    }

    /**
     * @author embeddedt (issue found by Uncandango)
     * @reason Preserve one canonical named holder when mods request a new tag
     *         concurrently.
     */
    @Overwrite
    public HolderSet.Named<T> getOrCreateTag(TagKey<T> key) {
        HolderSet.Named<T> named = this.tags.get(key);
        if (named == null) {
            synchronized (this) {
                named = this.tags.get(key);
                if (named == null) {
                    named = this.createTag(key);
                    Map<TagKey<T>, HolderSet.Named<T>> copy =
                            new IdentityHashMap<>(this.tags);
                    copy.put(key, named);
                    this.tags = copy;
                }
            }
        }
        return named;
    }
}
