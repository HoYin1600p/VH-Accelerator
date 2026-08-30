/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/searchtree/JEIBackedSearchTree.java
 * Upstream commit: 66ef30449a84c29186dd4c857eedb9d5e378fafb
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; adapted collect-before-iterate behavior to JEI 10's list-returning API.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.jei;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public final class JeiSearchSnapshot {
    private JeiSearchSnapshot() {
    }

    public static Iterator<?> iterator(List<?> liveResults) {
        return Arrays.asList(liveResults.toArray()).iterator();
    }
}
