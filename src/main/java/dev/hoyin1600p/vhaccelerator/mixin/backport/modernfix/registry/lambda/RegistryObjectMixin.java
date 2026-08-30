/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/mixin/perf/forge_registry_lambda/RegistryObjectMixin.java
 * Upstream commit: fe855f15304ed788122a27cda4c2495a78374528
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; isolated exact Forge 40 RegistryObject allocation removal.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.registry.lambda;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = RegistryObject.class, remap = false)
public abstract class RegistryObjectMixin<T> {
    @Shadow
    @Nullable
    private T value;

    @Shadow
    @Final
    private ResourceLocation name;

    /**
     * @author embeddedt
     * @reason Avoid allocating a capturing error-message supplier on every
     * successful RegistryObject lookup.
     */
    @Overwrite
    public T get() {
        T result = this.value;
        if (result == null) {
            throw new NullPointerException(
                    "Registry Object not present: " + this.name
            );
        }
        return result;
    }
}
