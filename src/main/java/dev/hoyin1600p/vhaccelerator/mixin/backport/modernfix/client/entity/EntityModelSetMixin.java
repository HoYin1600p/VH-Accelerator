/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/compact_entity_models/CubeDefinitionMixin.java
 * Upstream commit: 5a9c49f8d405502c5c1e50a42cf27a8597e541a0
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; bound retained cubes to the current entity-model resource generation.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.entity;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.entity.EntityModelCubeCache;
import net.minecraft.client.model.geom.EntityModelSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityModelSet.class)
public abstract class EntityModelSetMixin {
    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void vha$beginEntityModelResourceGeneration(
            CallbackInfo callback
    ) {
        EntityModelCubeCache.beginResourceGeneration();
    }
}
