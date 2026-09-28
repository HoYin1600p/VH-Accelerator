/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/compact_entity_models/CubeDefinitionMixin.java
 * Upstream commit: 5a9c49f8d405502c5c1e50a42cf27a8597e541a0
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; added primitive keys, external initialization, and resource-generation cleanup.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.entity;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.client.model.geom.ModelPart;

public final class EntityModelCubeCache {
    private static final ConcurrentMap<CubeKey, ModelPart.Cube> CUBES =
            new ConcurrentHashMap<>();

    private EntityModelCubeCache() {
    }

    public static ModelPart.Cube find(CubeKey key) {
        return CUBES.get(key);
    }

    public static ModelPart.Cube publish(
            CubeKey key,
            ModelPart.Cube created
    ) {
        ModelPart.Cube existing = CUBES.putIfAbsent(key, created);
        return existing == null ? created : existing;
    }

    public static void beginResourceGeneration() {
        CUBES.clear();
    }

    static int size() {
        return CUBES.size();
    }

    public record CubeKey(
            int textureU,
            int textureV,
            int originX,
            int originY,
            int originZ,
            int dimensionX,
            int dimensionY,
            int dimensionZ,
            int growX,
            int growY,
            int growZ,
            boolean mirror,
            int textureScaleU,
            int textureScaleV
    ) {
        public static CubeKey of(
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
            return new CubeKey(
                    textureU,
                    textureV,
                    Float.floatToIntBits(originX),
                    Float.floatToIntBits(originY),
                    Float.floatToIntBits(originZ),
                    Float.floatToIntBits(dimensionX),
                    Float.floatToIntBits(dimensionY),
                    Float.floatToIntBits(dimensionZ),
                    Float.floatToIntBits(growX),
                    Float.floatToIntBits(growY),
                    Float.floatToIntBits(growZ),
                    mirror,
                    Float.floatToIntBits(textureScaleU),
                    Float.floatToIntBits(textureScaleV)
            );
        }
    }
}
