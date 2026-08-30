/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/forge_cap_retrieval/AttachCapabilitiesEventMixin.java
 * Upstream commit: d699187006cecd6a8294f0048aaa2041e6e8b967
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-29; isolated behind VHA's restart-bound ownership gate.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.capability;

import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Supplies the constant non-cancelable answer that Forge's event transformer
 * cannot add through {@code GenericEvent}'s separate class-loader layer.
 */
@Mixin(AttachCapabilitiesEvent.class)
public abstract class AttachCapabilitiesEventMixin extends Event {
    @Override
    public boolean isCancelable() {
        return false;
    }
}
