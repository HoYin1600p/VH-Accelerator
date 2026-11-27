/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/background_system_info/GLXMixin.java
 * Upstream commit: 64ce25a1607e74f8e4c8972512774f124ef460dc
 * Original copyright: Copyright (c) 2026; embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-11-27; redirected oshi.SystemInfo construction in GLX._init to SharedSystemInfo.get().
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.systeminfo;

import com.mojang.blaze3d.platform.GLX;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.systeminfo.SharedSystemInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import oshi.SystemInfo;

@Mixin(GLX.class)
public abstract class GlxOshiMixin {
    @Redirect(
            method = "_init",
            at = @At(value = "NEW", target = "oshi/SystemInfo", remap = false),
            require = 1
    )
    private static SystemInfo vhaccelerator$useSharedSystemInfo() {
        return SharedSystemInfo.get();
    }
}
