/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/background_system_info/SystemReportMixin.java
 * Upstream commit: 64ce25a1607e74f8e4c8972512774f124ef460dc
 * Original copyright: Copyright (c) 2026; embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-11-27; redirected oshi.SystemInfo construction in SystemReport to SharedSystemInfo.get().
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.systeminfo;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.systeminfo.SharedSystemInfo;
import net.minecraft.SystemReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import oshi.SystemInfo;

@Mixin(SystemReport.class)
public abstract class SystemReportOshiMixin {
    @Redirect(
            method = "*",
            at = @At(value = "NEW", target = "oshi/SystemInfo", remap = false),
            require = 1
    )
    private SystemInfo vhaccelerator$useSharedSystemInfo() {
        return SharedSystemInfo.get();
    }
}
