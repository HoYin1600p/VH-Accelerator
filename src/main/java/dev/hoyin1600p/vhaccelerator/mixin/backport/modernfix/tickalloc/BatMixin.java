/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted from ModernFix's ticking-chunk allocation reductions.
 * See THIRD_PARTY_NOTICES.md and docs/MODERNFIX_BACKPORTS.md.
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
