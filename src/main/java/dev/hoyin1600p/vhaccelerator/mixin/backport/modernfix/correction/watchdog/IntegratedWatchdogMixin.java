/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/world/IntegratedWatchdog.java
 * Upstream commit: bc7aa5539c351d65e1e5b2b87b7c767367f4be52
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; applied commits 3bad8f59 and bc7aa553 as an optional ModernFix 5.18 companion patch.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.correction.watchdog;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.watchdog.IntegratedWatchdogTiming;
import java.util.OptionalLong;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(
        targets = "org.embeddedt.modernfix.world.IntegratedWatchdog",
        remap = false
)
public abstract class IntegratedWatchdogMixin {
    @Redirect(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/OptionalLong;getAsLong()J",
                    ordinal = 0
            ),
            require = 1
    )
    private long vha$pauseWhileIntegratedServerBoots(
            OptionalLong lastTickStart
    ) {
        long value = lastTickStart.getAsLong();
        if (IntegratedWatchdogTiming.serverIsBooting(value)) {
            try {
                Thread.sleep(
                        IntegratedWatchdogTiming.SERVER_BOOT_RETRY_MILLIS
                );
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        return value;
    }

    @ModifyArg(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Thread;sleep(J)V"
            ),
            index = 0,
            require = 1
    )
    private long vha$avoidNegativeWatchdogSleep(long requestedDelay) {
        return IntegratedWatchdogTiming.safeSleepDelay(requestedDelay);
    }
}
