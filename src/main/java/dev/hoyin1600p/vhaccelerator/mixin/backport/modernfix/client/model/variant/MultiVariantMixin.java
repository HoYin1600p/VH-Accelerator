/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/model_optimizations/MultiVariantMixin.java
 * Upstream commit: 3ad4e2478e6965e902d1a77e2483d770d0a363d3
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; applied the allocation-light single-variant and
 * deduplicated-loop design to 1.18.2's dependency/material traversal methods.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.model.variant;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = MultiVariant.class, priority = 700)
public abstract class MultiVariantMixin {
    @Shadow
    public abstract List<Variant> getVariants();

    /**
     * @author embeddedt, HoYin1600p
     * @reason Avoid stream pipelines and set growth for the overwhelmingly
     * common zero/one-variant dependency cases.
     */
    @Overwrite
    public Collection<ResourceLocation> getDependencies() {
        List<Variant> variants = this.getVariants();
        if (variants.isEmpty()) {
            return Collections.emptySet();
        }
        if (variants.size() == 1) {
            return Collections.singleton(
                    variants.get(0).getModelLocation()
            );
        }
        ObjectOpenHashSet<ResourceLocation> dependencies =
                new ObjectOpenHashSet<>(variants.size());
        for (Variant variant : variants) {
            dependencies.add(variant.getModelLocation());
        }
        return dependencies;
    }

    /**
     * @author embeddedt, HoYin1600p
     * @reason Resolve each distinct child model once without constructing
     * chained map/distinct/flatMap/collect streams.
     */
    @Overwrite
    public Collection<Material> getMaterials(
            Function<ResourceLocation, UnbakedModel> modelGetter,
            Set<Pair<String, String>> missingTextureErrors
    ) {
        List<Variant> variants = this.getVariants();
        if (variants.isEmpty()) {
            return Collections.emptySet();
        }

        ObjectOpenHashSet<ResourceLocation> visited =
                new ObjectOpenHashSet<>(variants.size());
        ObjectOpenHashSet<Material> materials = new ObjectOpenHashSet<>();
        for (Variant variant : variants) {
            ResourceLocation location = variant.getModelLocation();
            if (visited.add(location)) {
                materials.addAll(modelGetter.apply(location).getMaterials(
                        modelGetter,
                        missingTextureErrors
                ));
            }
        }
        return materials;
    }
}
