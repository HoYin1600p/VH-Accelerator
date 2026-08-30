/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/core/WorldLoaderMixin.java
 * Upstream commit: 5048e74c79724029a8ba2e3d7cdd37c83320ad4e
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted both initial and command-triggered server
 * data reloads through Minecraft 1.18.2 ReloadableServerResources.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.recipe;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe.IngredientReloadTracker;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {
    @Inject(method = "loadResources", at = @At("HEAD"))
    private static void vha$beginIngredientReload(
            ResourceManager resourceManager,
            RegistryAccess.Frozen registryAccess,
            Commands.CommandSelection commandSelection,
            int functionCompilationLevel,
            Executor backgroundExecutor,
            Executor gameExecutor,
            CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> cir
    ) {
        IngredientReloadTracker.begin();
    }

    @Inject(method = "loadResources", at = @At("RETURN"), cancellable = true)
    private static void vha$finishIngredientReload(
            ResourceManager resourceManager,
            RegistryAccess.Frozen registryAccess,
            Commands.CommandSelection commandSelection,
            int functionCompilationLevel,
            Executor backgroundExecutor,
            Executor gameExecutor,
            CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> cir
    ) {
        CompletableFuture<ReloadableServerResources> reload = cir.getReturnValue();
        if (reload == null) {
            IngredientReloadTracker.finish();
            return;
        }
        cir.setReturnValue(reload.whenComplete(
                (resources, failure) -> IngredientReloadTracker.finish()
        ));
    }
}
