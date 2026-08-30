/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/fix_handshake_stall/HandshakeHandlerMixin.java
 * Upstream commit: c2f585da9551d925c01b391ddd151e02c5037382
 * Original copyright: embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-29; extracted the progress loop into a testable helper.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.network;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class ForgeHandshakeBatcher {
    private ForgeHandshakeBatcher() {
    }

    public static boolean tickUntilBlocked(
            IntSupplier packetPosition,
            BooleanSupplier originalTick
    ) {
        boolean done;
        int previousPosition;
        do {
            previousPosition = packetPosition.getAsInt();
            done = originalTick.getAsBoolean();
        } while (!done && packetPosition.getAsInt() > previousPosition);
        return done;
    }
}
