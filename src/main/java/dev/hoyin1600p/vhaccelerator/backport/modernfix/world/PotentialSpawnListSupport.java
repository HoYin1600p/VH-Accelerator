/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted from ModernFix's Forge potential-spawn allocation reduction.
 * See THIRD_PARTY_NOTICES.md and docs/MODERNFIX_BACKPORTS.md.
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
