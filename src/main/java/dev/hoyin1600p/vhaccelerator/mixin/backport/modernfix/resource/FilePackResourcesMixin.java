/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/resourcepacks/FilePackResourcesMixin.java
 * Upstream commit: 62dbbea083d52adfb4ac194aa290ed46e310abb8
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted Minecraft 1.18.2's FilePackResources
 * signatures and preserved the original ZIP lifecycle and fallback behavior.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.resource;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.resource.ZipPackIndex;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.ZipFile;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FilePackResources.class)
public abstract class FilePackResourcesMixin {
    @Unique
    private volatile ZipPackIndex vhaccelerator$zipIndex;
    @Unique
    private volatile boolean vhaccelerator$zipIndexRejected;

    @Shadow
    private ZipFile getOrCreateZipFile() throws IOException {
        throw new AssertionError();
    }

    @Inject(method = "getNamespaces", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$useIndexedNamespaces(
            PackType type,
            CallbackInfoReturnable<Set<String>> callback
    ) {
        ZipPackIndex index = vhaccelerator$getOrCreateIndex();
        if (index != null) {
            callback.setReturnValue(index.namespaces(type));
        }
    }

    @Inject(method = "getResources", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$useIndexedResources(
            PackType type,
            String namespace,
            String path,
            int maxDepth,
            Predicate<String> filter,
            CallbackInfoReturnable<Collection<ResourceLocation>> callback
    ) {
        ZipPackIndex index = vhaccelerator$getOrCreateIndex();
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

    @Inject(method = "close", at = @At("HEAD"))
    private void vhaccelerator$discardZipIndex(CallbackInfo callback) {
        this.vhaccelerator$zipIndex = null;
        this.vhaccelerator$zipIndexRejected = false;
    }

    @Unique
    @Nullable
    private ZipPackIndex vhaccelerator$getOrCreateIndex() {
        ZipPackIndex current = this.vhaccelerator$zipIndex;
        if (current != null) {
            return current;
        }
        if (this.vhaccelerator$zipIndexRejected) {
            return null;
        }
        synchronized (this) {
            current = this.vhaccelerator$zipIndex;
            if (current != null) {
                return current;
            }
            if (this.vhaccelerator$zipIndexRejected) {
                return null;
            }
            FilePackResources pack = (FilePackResources) (Object) this;
            try {
                if (getOrCreateZipFile() == null) {
                    this.vhaccelerator$zipIndexRejected = true;
                    return null;
                }
                current = new ZipPackIndex(pack.file.toPath());
                this.vhaccelerator$zipIndex = current;
                return current;
            } catch (IOException | RuntimeException | LinkageError failure) {
                this.vhaccelerator$zipIndexRejected = true;
                VHAccelerator.LOGGER.debug(
                        "ZIP resource index deferred {} to Minecraft's original path",
                        pack.file,
                        failure
                );
                return null;
            }
        }
    }
}
