/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/ticking_chunk_alloc/BatMixin.java
 * Upstream commit: 070b7b6d126af5d04cb6a4315d823a2ad6dbc6bb
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: moved cached date state to a normal holder
 * class and added independent exact-option ownership.
 * Modified: 2026-08-30
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.tickalloc;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.world.CachedLocalDate;
import java.time.LocalDate;
import net.minecraft.world.entity.ambient.Bat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Bat.class, priority = 1200)
public final class BatMixin {
    @Redirect(
            method = "isHalloween",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/time/LocalDate;now()Ljava/time/LocalDate;"
            ),
            require = 0
    )
    private static LocalDate vha$useCachedDate() {
        return CachedLocalDate.now();
    }
}
