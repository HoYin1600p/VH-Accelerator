/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/mixin/perf/resourcepacks/VanillaPackMixin.java
 * Upstream commit: debfbdc017340186401a61f7f302bb733927a90d
 * Original copyright: Copyright (c) 2023 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; replaced the classloader overwrite with guarded tree
 * queries, retained the original stream path, and automatically falls back for
 * generated resources, mutable development roots, and OptiFine.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.resource;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.resource.ImmutablePathPackIndex;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VanillaPackResources.class)
public abstract class VanillaPackResourcesMixin {
    @Shadow
    @Final
    private static Map<PackType, Path> ROOT_DIR_BY_TYPE;

    @Shadow
    public static Path generatedDir;

    @Unique
    private static volatile ImmutablePathPackIndex
            vhaccelerator$vanillaIndex;
    @Unique
    private static volatile boolean vhaccelerator$indexChecked;

    @Inject(method = "getResources", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$listIndexedVanillaResources(
            PackType type,
            String namespace,
            String path,
            int maxDepth,
            Predicate<String> filter,
            CallbackInfoReturnable<Collection<ResourceLocation>> callback
    ) {
        ImmutablePathPackIndex index = vhaccelerator$index();
        if (index == null) {
            return;
        }
        Collection<ResourceLocation> resources = index.resources(
                type,
                namespace,
                path,
                maxDepth,
                filter
        );
        if (resources != null) {
            callback.setReturnValue(resources);
        }
    }

    @Inject(method = "hasResource", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$findIndexedVanillaResource(
            PackType type,
            ResourceLocation location,
            CallbackInfoReturnable<Boolean> callback
    ) {
        ImmutablePathPackIndex index = vhaccelerator$index();
        if (index == null) {
            return;
        }
        Boolean exists = index.hasResource(
                type.getDirectory()
                        + "/" + location.getNamespace()
                        + "/" + location.getPath()
        );
        if (exists != null) {
            callback.setReturnValue(exists);
        }
    }

    @Unique
    private static ImmutablePathPackIndex vhaccelerator$index() {
        if (generatedDir != null) {
            return null;
        }
        if (!vhaccelerator$indexChecked) {
            synchronized (VanillaPackResourcesMixin.class) {
                if (!vhaccelerator$indexChecked) {
                    vhaccelerator$vanillaIndex =
                            ImmutablePathPackIndex.createVanilla(
                                    ROOT_DIR_BY_TYPE
                            );
                    vhaccelerator$indexChecked = true;
                }
            }
        }
        return vhaccelerator$vanillaIndex;
    }
}
