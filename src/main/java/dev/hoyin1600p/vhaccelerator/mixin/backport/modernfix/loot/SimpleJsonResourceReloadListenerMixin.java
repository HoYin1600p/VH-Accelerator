/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_loot_loading/LootDataManagerMixin.java
 * Upstream commit: 0a68e874e98a1476bc36cedfbf2f1e3ee64bcbcb
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted source capture to the existing 1.18.2 JSON
 * resource read and records metadata only for LootTables instances.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.loot;

import java.io.IOException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class SimpleJsonResourceReloadListenerMixin {
    @Inject(method = "prepare", at = @At("HEAD"))
    private void vhaccelerator$beginOriginCapture(
            ResourceManager resourceManager,
            net.minecraft.util.profiling.ProfilerFiller profiler,
            CallbackInfoReturnable<?> callback
    ) {
        if ((Object) this instanceof LootResourceOriginAccess access) {
            access.vhaccelerator$lootResourceOrigins().clear();
        }
    }

    @Redirect(
            method = "prepare",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/ResourceManager;"
                            + "getResource(Lnet/minecraft/resources/ResourceLocation;)"
                            + "Lnet/minecraft/server/packs/resources/Resource;"
            )
    )
    private Resource vhaccelerator$captureResourceOrigin(
            ResourceManager resourceManager,
            ResourceLocation location
    ) throws IOException {
        Resource resource = resourceManager.getResource(location);
        if ((Object) this instanceof LootResourceOriginAccess access) {
            access.vhaccelerator$lootResourceOrigins().record(resource);
        }
        return resource;
    }
}
