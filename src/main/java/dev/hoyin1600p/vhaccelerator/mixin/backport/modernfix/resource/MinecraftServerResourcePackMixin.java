/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/resourcepacks/MinecraftServerMixin.java
 * Upstream commit: e9bfd96dd9b997a5a9c468d9e164b4d144cfec12
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted Forge 40 and replaced MixinExtras with a
 * standard redirect backed by weak external state.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.resource;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.resource.ForgePackFinderDeduplicator;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.resource.PathResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerResourcePackMixin {
    @Redirect(
            method = "configurePackRepository",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/resource/ResourcePackLoader;loadResourcePacks(Lnet/minecraft/server/packs/repository/PackRepository;Ljava/util/function/Function;)V",
                    remap = false
            )
    )
    private static void vhaccelerator$loadForgePacksOnce(
            PackRepository repository,
            Function<
                    Map<IModFile, ? extends PathResourcePack>,
                    ? extends RepositorySource
            > packFinder
    ) {
        ForgePackFinderDeduplicator.loadOnce(repository, packFinder);
    }
}
