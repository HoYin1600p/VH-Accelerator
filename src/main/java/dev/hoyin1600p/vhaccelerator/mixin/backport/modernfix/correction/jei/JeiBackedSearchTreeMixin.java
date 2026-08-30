/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/searchtree/JEIBackedSearchTree.java
 * Upstream commit: 66ef30449a84c29186dd4c857eedb9d5e378fafb
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; snapshots JEI 10 list results before ModernFix iterates them.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.correction.jei;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.jei.JeiSearchSnapshot;
import java.util.Iterator;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(
        targets = "org.embeddedt.modernfix.searchtree.JEIBackedSearchTree",
        remap = false
)
public abstract class JeiBackedSearchTreeMixin {
    @Redirect(
            method = "searchJEI",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;iterator()Ljava/util/Iterator;"
            ),
            require = 1
    )
    private Iterator<?> vha$iterateStableSnapshot(List<?> liveResults) {
        return JeiSearchSnapshot.iterator(liveResults);
    }
}
