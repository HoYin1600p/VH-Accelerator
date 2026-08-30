/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/fix_handshake_stall/HandshakeHandlerMixin.java
 * Upstream commit: c2f585da9551d925c01b391ddd151e02c5037382
 * Original copyright: embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-29; added an accessor for a MixinExtras-free redirect.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.network;

import net.minecraftforge.network.HandshakeHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = HandshakeHandler.class, remap = false)
public interface HandshakeHandlerAccess {
    @Accessor("packetPosition")
    int vha$getPacketPosition();
}
