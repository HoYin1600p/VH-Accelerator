/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/dedup/IdentifierCaches.java
 * Upstream commit: b6ae90d384774cda05aaac4ccc7e4ee3f9c246be
 * Original copyright: Copyright (c) 2022-2023 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; restricted deduplication to namespaces, replaced the
 * synchronized strong pool with a bounded lock-free table, and added
 * debug-only statistics.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.registry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Canonical namespace strings for {@code ResourceLocation}s.
 *
 * <p>Parsing {@code "minecraft:stone"} splits out a fresh {@code "minecraft"}
 * string for every identifier, so each retained identifier parsed from text
 * (recipe, tag, model, texture and loot IDs) carries its own copy of one of a
 * few hundred namespaces. Paths are deliberately not pooled: a path pool is
 * either unbounded or needs weak references, and costs more on the launch
 * path than it saves.</p>
 *
 * <p>Lookups are lock-free; construction runs concurrently on model and data
 * loading workers. The table stops growing at {@link #MAX_NAMESPACES}, after
 * which unknown namespaces are returned unchanged.</p>
 */
public final class ResourceLocationNamespaces {
    static final int MAX_NAMESPACES = 4_096;
    private static final ConcurrentHashMap<String, String> NAMESPACES =
            new ConcurrentHashMap<>(1_024);
    private static final LongAdder DEDUPLICATED = new LongAdder();
    private static volatile boolean statistics;

    private ResourceLocationNamespaces() {
    }

    public static String canonical(String namespace) {
        String existing = NAMESPACES.get(namespace);
        if (existing == null) {
            if (NAMESPACES.size() >= MAX_NAMESPACES) {
                return namespace;
            }
            existing = NAMESPACES.putIfAbsent(namespace, namespace);
            if (existing == null) {
                return namespace;
            }
        }
        if (statistics && existing != namespace) {
            DEDUPLICATED.increment();
        }
        return existing;
    }

    /** Enables the debug-only duplicate counter. */
    public static void enableStatistics() {
        statistics = true;
    }

    /** Duplicate namespace strings replaced since statistics were enabled. */
    public static long deduplicated() {
        return DEDUPLICATED.sum();
    }

    public static int pooled() {
        return NAMESPACES.size();
    }

    static void clearForTest() {
        NAMESPACES.clear();
        DEDUPLICATED.reset();
        statistics = false;
    }
}
