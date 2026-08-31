/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/mixin/perf/resourcepacks/ModFileResourcePackMixin.java
 * Upstream commit: debfbdc017340186401a61f7f302bb733927a90d
 * Original copyright: Copyright (c) 2023 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted Forge 40's PathResourcePack, limited caching
 * to immutable filesystems, retained language fallback, and avoided overriding
 * mutable/generated pack implementations.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.resource;

import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.resource.ImmutablePathPackIndex;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.resource.PathResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PathResourcePack.class, priority = 1100)
public abstract class PathResourcePackIndexMixin {
    @Unique
    private ImmutablePathPackIndex vhaccelerator$resourceIndex;
    @Unique
    private boolean vhaccelerator$indexChecked;

    @Shadow(remap = false)
    public abstract Path getSource();

    @Shadow(remap = false)
    protected abstract Path resolve(String... paths);

    @Inject(method = "getNamespaces", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$useCachedNamespaces(
            PackType type,
            CallbackInfoReturnable<Set<String>> callback
    ) {
        ImmutablePathPackIndex index = vhaccelerator$index();
        if (index == null) {
            return;
        }
        Set<String> namespaces = index.cachedNamespaces(type);
        if (namespaces != null) {
            callback.setReturnValue(namespaces);
        }
    }

    @Inject(method = "getResources", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$listIndexedResources(
            PackType type,
            String namespace,
            String prefix,
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
                prefix,
                maxDepth,
                filter
        );
        if (resources != null) {
            callback.setReturnValue(resources);
        }
    }

    @Inject(
            method = "hasResource(Ljava/lang/String;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vhaccelerator$findIndexedResource(
            String name,
            CallbackInfoReturnable<Boolean> callback
    ) {
        ImmutablePathPackIndex index = vhaccelerator$index();
        if (index == null) {
            return;
        }
        Boolean exists = index.hasResource(name);
        if (exists != null) {
            callback.setReturnValue(exists);
        }
    }

    @Unique
    private ImmutablePathPackIndex vhaccelerator$index() {
        if (!VHAcceleratorConfig.commonOptimizationsEnabled()
                || !VHAcceleratorConfig.COMMON
                .indexImmutableModResources
                .get()) {
            return null;
        }
        if (!this.vhaccelerator$indexChecked) {
            this.vhaccelerator$indexChecked = true;
            this.vhaccelerator$resourceIndex =
                    ImmutablePathPackIndex.create(
                            getSource(),
                            this::resolve
                    );
        }
        return this.vhaccelerator$resourceIndex;
    }
}
