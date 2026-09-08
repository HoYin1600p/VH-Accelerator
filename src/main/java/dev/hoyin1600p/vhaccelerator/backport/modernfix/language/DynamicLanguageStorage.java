/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/dynamiclanguages/DynamicLanguageMap.java
 * Upstream commit: d749205427d714a4865155f03c16a48f8e564117
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-08; retain and deduplicate the already-parsed language
 * snapshot instead of re-reading source files at construction and lookup.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.language;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The legacy config/mixin name is retained for compatibility. Values now belong
 * to one immutable resource-reload snapshot: no soft-reference eviction,
 * resource handles, ThreadLocal load contexts or disk reads during lookup.
 */
public final class DynamicLanguageStorage {
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");

    private DynamicLanguageStorage() { }

    public static BuildResult createStorage(Map<String, String> parsed) {
        var storage = new Object2ObjectOpenHashMap<String, String>(parsed.size());
        var values = new Object2ObjectOpenHashMap<String, String>();
        int deduplicated = 0;
        long characters = 0;
        for (var entry : parsed.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey());
            String value = Objects.requireNonNull(entry.getValue());
            String canonical = values.putIfAbsent(value, value);
            if (canonical != null) {
                if (canonical != value) {
                    deduplicated++;
                    characters += value.length();
                }
                value = canonical;
            }
            storage.put(key, value);
        }
        return new BuildResult(Collections.unmodifiableMap(storage), deduplicated, characters);
    }

    public static void log(BuildResult result) {
        LOGGER.info("Prepared immutable language snapshot: {} entries, {} duplicate values shared ({} characters); no lookup-time resource reads",
                result.storage().size(), result.deduplicatedValues(), result.sharedCharacters());
    }

    public record BuildResult(Map<String, String> storage, int deduplicatedValues, long sharedCharacters) { }
}
