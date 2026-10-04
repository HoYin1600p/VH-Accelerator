package dev.hoyin1600p.vhaccelerator.client.model.parse;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Shares the path strings of model locations. Every block-state and item
 * model location carries its own copy of its block or item path (one per
 * state: 1.3 M locations over roughly 60,000 distinct paths in Wolds Vaults,
 * about 80 MB of copies). Strings are immutable, so sharing one instance per
 * value changes nothing but memory. The table is bounded; past the bound new
 * paths simply keep their own copy.
 */
public final class ModelLocationPaths {
    static final int MAX_PATHS = 262_144;
    private static final ConcurrentHashMap<String, String> PATHS = new ConcurrentHashMap<>(65_536);
    /** On until the client config is read; locations built before that are shared too. */
    private static volatile boolean enabled = true;

    private ModelLocationPaths() {
    }

    public static String canonical(String path) {
        String existing = PATHS.get(path);
        if (existing != null) {
            return existing;
        }
        if (PATHS.size() >= MAX_PATHS) {
            return path;
        }
        existing = PATHS.putIfAbsent(path, path);
        return existing == null ? path : existing;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void configure(boolean value) {
        enabled = value;
    }

    public static int pooled() {
        return PATHS.size();
    }
}
