/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/bugfix/concurrency/ForgeRegistryTagManagerMixin.java
 * Upstream commit: 87c977a3e6e3bfbda45a805642d94ebffb29ce14
 * Original copyright: Copyright (c) 2025 embeddedt, Uncandango, and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; replaced MixinExtras with a Forge 40 overwrite and lazy factory.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.tag;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.tag.ForgeRegistryTagFactory;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.tags.ITag;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(
        targets = "net.minecraftforge.registries.ForgeRegistryTagManager",
        priority = 500
)
public abstract class ForgeRegistryTagManagerMixin<V> {
    @Shadow(remap = false)
    private volatile Map<TagKey<V>, ITag<V>> tags;

    /**
     * @author embeddedt (issue found by Uncandango)
     * @reason Forge 40's copy-on-write tag manager can otherwise publish and
     *         return different tag wrappers to concurrent callers.
     */
    @Overwrite(remap = false)
    @NotNull
    public ITag<V> getTag(@NotNull TagKey<V> name) {
        Objects.requireNonNull(name);
        ITag<V> tag = this.tags.get(name);
        if (tag == null) {
            synchronized (this) {
                tag = this.tags.get(name);
                if (tag == null) {
                    tag = ForgeRegistryTagFactory.create(name);
                    IdentityHashMap<TagKey<V>, ITag<V>> copy =
                            new IdentityHashMap<>(this.tags);
                    copy.put(name, tag);
                    this.tags = copy;
                }
            }
        }
        return tag;
    }
}
