/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/resourcepacks/MinecraftServerMixin.java
 * Upstream commit: e9bfd96dd9b997a5a9c468d9e164b4d144cfec12
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; extracted weak identity tracking from the mixin and
 * made a failed Forge injection eligible for a later retry.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.resource;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.resource.PathResourcePack;
import net.minecraftforge.resource.ResourcePackLoader;

/** Prevents Forge from adding a new equivalent mod-pack finder repeatedly. */
public final class ForgePackFinderDeduplicator {
    private static final Set<PackRepository> INJECTED_REPOSITORIES =
            Collections.synchronizedSet(
                    Collections.newSetFromMap(new WeakHashMap<>())
            );

    private ForgePackFinderDeduplicator() {
    }

    public static void loadOnce(
            PackRepository repository,
            Function<
                    Map<IModFile, ? extends PathResourcePack>,
                    ? extends RepositorySource
            > packFinder
    ) {
        if (!mark(repository)) {
            return;
        }
        try {
            ResourcePackLoader.loadResourcePacks(repository, packFinder);
        } catch (RuntimeException | Error failure) {
            INJECTED_REPOSITORIES.remove(repository);
            throw failure;
        }
    }

    static boolean mark(Object repository) {
        if (!(repository instanceof PackRepository packRepository)) {
            return false;
        }
        return INJECTED_REPOSITORIES.add(packRepository);
    }
}
