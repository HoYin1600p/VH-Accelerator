/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/mixin/core/BootstrapMixin.java
 * Upstream commit: 73c80d0603849b9bb131d7712316d0a2ecf4938f
 * Original copyright: Copyright (c) 2023 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; installs the queue when client mod loading begins,
 * after ModernFix's bootstrap-time replacement, instead of at bootstrap.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.load;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.load.ResponsiveModWorkQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraftforge.client.loading.ClientModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientModLoader.class, remap = false)
public abstract class ClientModLoaderWorkQueueMixin {
    @Inject(method = "begin", at = @At("HEAD"))
    private static void vhaccelerator$installResponsiveQueue(
            Minecraft minecraft,
            PackRepository packRepository,
            ReloadableResourceManager resourceManager,
            ClientPackSource clientPackSource,
            CallbackInfo callback
    ) {
        try {
            if (!ResponsiveModWorkQueue.install()) {
                VHAccelerator.LOGGER.warn("Could not install the responsive mod-loading work queue");
            }
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.warn("Could not install the responsive mod-loading work queue", failure);
        }
    }
}
