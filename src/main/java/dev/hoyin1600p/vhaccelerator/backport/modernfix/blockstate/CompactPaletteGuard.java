/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/compact_bit_storage/PalettedContainerMixin.java
 * Upstream commit: 4a8e0487bc8a0fd602d2b5bd83613a8ce3c4e33e
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; extracted exception-safe palette access for focused testing.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.blockstate;

import java.util.Optional;
import java.util.function.IntFunction;

public final class CompactPaletteGuard {
    private CompactPaletteGuard() {
    }

    public static <T> Optional<T> firstValue(
            IntFunction<T> paletteLookup
    ) {
        try {
            return Optional.ofNullable(paletteLookup.apply(0));
        } catch (RuntimeException invalidPalette) {
            return Optional.empty();
        }
    }

    public static boolean storageIsEmpty(long[] storage) {
        for (long value : storage) {
            if (value != 0L) {
                return false;
            }
        }
        return true;
    }
}
