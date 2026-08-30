/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/bugfix/concurrency/ForgeRegistryTagManagerMixin.java
 * Upstream commit: 87c977a3e6e3bfbda45a805642d94ebffb29ce14
 * Original copyright: Copyright (c) 2025 embeddedt, Uncandango, and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; added a Forge 40 factory for its package-private tag implementation.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.tag;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.tags.ITag;

/** Creates Forge 40's package-private tag wrapper only on a cache miss. */
public final class ForgeRegistryTagFactory {
    private ForgeRegistryTagFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <V> ITag<V> create(TagKey<V> key) {
        try {
            return (ITag<V>) ConstructorHolder.CONSTRUCTOR.newInstance(key);
        } catch (InstantiationException
                 | IllegalAccessException
                 | InvocationTargetException failure) {
            throw new IllegalStateException(
                    "Could not create ForgeRegistryTag on Forge 40",
                    failure
            );
        }
    }

    private static final class ConstructorHolder {
        private static final Constructor<?> CONSTRUCTOR = findConstructor();

        private static Constructor<?> findConstructor() {
            try {
                Class<?> tagClass = Class.forName(
                        "net.minecraftforge.registries.ForgeRegistryTag",
                        false,
                        ForgeRegistryTagFactory.class.getClassLoader()
                );
                Constructor<?> constructor =
                        tagClass.getDeclaredConstructor(TagKey.class);
                constructor.setAccessible(true);
                return constructor;
            } catch (ClassNotFoundException
                     | NoSuchMethodException
                     | RuntimeException failure) {
                throw new IllegalStateException(
                        "Forge 40 tag implementation is incompatible",
                        failure
                );
            }
        }
    }
}
