/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_loot_loading/LootDataManagerMixin.java
 * Upstream commit: 0a68e874e98a1476bc36cedfbf2f1e3ee64bcbcb
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; introduced a 1.18.2 reload-local resource-origin
 * bridge outside the reserved Mixin package so transformed Minecraft classes
 * can reference it safely.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.loot;

public interface LootResourceOriginAccess {
    LootResourceOriginCache vhaccelerator$lootResourceOrigins();
}
