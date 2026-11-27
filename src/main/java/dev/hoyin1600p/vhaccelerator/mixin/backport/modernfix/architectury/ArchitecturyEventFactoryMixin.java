/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/architectury_event_dispatch/EventFactoryMixin.java
 * Upstream commit: 0ce7f03ce75d27e8a5c0c3137d5a4c8d89cf2c2d
 * Original copyright: Copyright (c) 2026; embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-11-27; retargeted as @Pseudo mixin for Architectury 4.x on Forge 1.18.2, delegating to ArchitecturyEventInvokers.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.architectury;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.architectury.ArchitecturyEventInvokers;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "dev.architectury.event.EventFactory", remap = false)
public abstract class ArchitecturyEventFactoryMixin {
    /**
     * @author HoYin1600p (adapted from embeddedt/ModernFix)
     * @reason Avoid unreflecting and adapting a new MethodHandle on every Architectury listener call.
     */
    @Overwrite
    private static Object invokeMethod(Object listener, Method method, Object[] args) throws Throwable {
        return ArchitecturyEventInvokers.invoke(MethodHandles.lookup(), listener, method, args);
    }
}
