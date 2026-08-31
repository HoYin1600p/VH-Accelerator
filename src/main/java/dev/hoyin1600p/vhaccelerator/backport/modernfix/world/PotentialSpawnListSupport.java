/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/mixin/perf/potential_spawns_alloc/PotentialSpawnsMixin.java
 * Upstream commit: 1814bd3e1faff1518119a7b5fcf85784c796023d
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: moved the constructor sentinel outside the
 * transformed Forge event class to avoid merged static initialization.
 * Modified: 2026-08-30
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.world;

import java.util.ArrayList;
import net.minecraft.world.level.biome.MobSpawnSettings;

public final class PotentialSpawnListSupport {
    private static final ArrayList<MobSpawnSettings.SpawnerData> SENTINEL =
            new ArrayList<>(0);

    private PotentialSpawnListSupport() {
    }

    public static ArrayList<MobSpawnSettings.SpawnerData> sentinel() {
        return SENTINEL;
    }
}
