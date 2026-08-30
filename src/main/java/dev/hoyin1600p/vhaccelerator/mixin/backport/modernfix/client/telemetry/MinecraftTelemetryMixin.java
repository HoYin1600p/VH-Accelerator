/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/feature/remove_telemetry/ClientTelemetryManagerMixin.java
 * Upstream commit: b72959f257480258cc869897d5ab32f2612a3c55
 * Original copyright: Copyright (c) 2024 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted Minecraft 1.18.2 by wrapping the final
 * UserApiService result rather than newer telemetry sender classes.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.telemetry;

import com.mojang.authlib.minecraft.UserApiService;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.telemetry.TelemetryBlockingUserApiService;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftTelemetryMixin {
    @Inject(
            method = "createUserApiService",
            at = @At("RETURN"),
            cancellable = true
    )
    private void vha$disableTelemetrySession(
            CallbackInfoReturnable<UserApiService> callback
    ) {
        UserApiService service = callback.getReturnValue();
        if (service != null
                && !(service instanceof TelemetryBlockingUserApiService)) {
            callback.setReturnValue(
                    new TelemetryBlockingUserApiService(service)
            );
        }
    }
}
