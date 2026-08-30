/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/cache_profile_texture_url/SkinManagerMixin.java
 * Upstream commit: e859ce8eb6b7b05c79179becf67df32e3efc4ad5
 * Original copyright: Copyright (c) 2023 embeddedt, Fury_Phoenix, and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted Minecraft 1.18.2 and delegates to a
 * bounded URL-keyed cache instead of retaining texture descriptor objects.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.skin;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.skin.ProfileTextureHashCache;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SkinManager.class)
public abstract class SkinManagerMixin {
    @Unique
    private final ProfileTextureHashCache vha$profileTextureHashCache =
            new ProfileTextureHashCache();

    @Redirect(
            method = "registerTexture(Lcom/mojang/authlib/minecraft/MinecraftProfileTexture;Lcom/mojang/authlib/minecraft/MinecraftProfileTexture$Type;Lnet/minecraft/client/resources/SkinManager$SkinTextureCallback;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/authlib/minecraft/MinecraftProfileTexture;getHash()Ljava/lang/String;",
                    remap = false
            )
    )
    private String vha$reuseProfileTextureHash(
            MinecraftProfileTexture texture
    ) {
        return vha$profileTextureHashCache.resolve(texture);
    }
}
