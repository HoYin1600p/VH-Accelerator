/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/compact_entity_models/CubeDefinitionMixin.java
 * Upstream commit: 5a9c49f8d405502c5c1e50a42cf27a8597e541a0
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted Minecraft 1.18.2 and replaced MixinExtras with a constructor redirect.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.entity;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.entity.EntityModelCubeCache;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.entity.EntityModelCubeCache.CubeKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CubeDefinition.class)
public abstract class CubeDefinitionMixin {
    @Redirect(
            method = "bake",
            at = @At(
                    value = "NEW",
                    target = "(IIFFFFFFFFFZFF)Lnet/minecraft/client/"
                            + "model/geom/ModelPart$Cube;"
            )
    )
    private ModelPart.Cube vha$deduplicateCube(
            int textureU,
            int textureV,
            float originX,
            float originY,
            float originZ,
            float dimensionX,
            float dimensionY,
            float dimensionZ,
            float growX,
            float growY,
            float growZ,
            boolean mirror,
            float textureScaleU,
            float textureScaleV
    ) {
        CubeKey key = CubeKey.of(
                textureU,
                textureV,
                originX,
                originY,
                originZ,
                dimensionX,
                dimensionY,
                dimensionZ,
                growX,
                growY,
                growZ,
                mirror,
                textureScaleU,
                textureScaleV
        );
        ModelPart.Cube cached = EntityModelCubeCache.find(key);
        if (cached != null) {
            return cached;
        }

        ModelPart.Cube created = new ModelPart.Cube(
                textureU,
                textureV,
                originX,
                originY,
                originZ,
                dimensionX,
                dimensionY,
                dimensionZ,
                growX,
                growY,
                growZ,
                mirror,
                textureScaleU,
                textureScaleV
        );
        return EntityModelCubeCache.publish(key, created);
    }
}
