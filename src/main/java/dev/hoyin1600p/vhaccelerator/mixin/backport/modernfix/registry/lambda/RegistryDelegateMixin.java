/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/mixin/perf/forge_registry_lambda/RegistryDelegateMixin.java
 * Upstream commit: fe855f15304ed788122a27cda4c2495a78374528
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; isolated exact Forge 40 delegate hash allocation removal.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.registry.lambda;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(
        targets = "net/minecraftforge/registries/RegistryDelegate",
        remap = false
)
public abstract class RegistryDelegateMixin {
    @Shadow
    private ResourceLocation name;

    /**
     * @author embeddedt
     * @reason Avoid Guava's varargs-array allocation for every delegate hash.
     */
    @Overwrite(remap = false)
    public int hashCode() {
        ResourceLocation currentName = this.name;
        return currentName == null ? 0 : currentName.hashCode();
    }
}
