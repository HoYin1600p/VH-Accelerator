package dev.hoyin1600p.vhaccelerator.client.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helpers for per-server persistent caches whose in-memory maps are keyed by
 * a server key, optionally followed by {@code ':'} or {@code '-'} and more
 * qualifiers. After use, only the current server's entries are kept, so a
 * reconnect or proxy (Velocity) backend switch to the same address never
 * rereads its cache from disk, while other servers' data is released.
 */
public final class ServerScopedCacheMemory {
    private ServerScopedCacheMemory() {
    }

    public static boolean belongsTo(String cacheKey, String serverKey) {
        if (!cacheKey.startsWith(serverKey)) {
            return false;
        }
        if (cacheKey.length() == serverKey.length()) {
            return true;
        }
        char separator = cacheKey.charAt(serverKey.length());
        return separator == ':' || separator == '-';
    }

    /** Returns a map of the same kind holding only {@code serverKey}'s entries. */
    public static <V> Map<String, V> retain(Map<String, V> all, String serverKey) {
        Map<String, V> kept = all instanceof ConcurrentHashMap<?, ?>
                ? new ConcurrentHashMap<>()
                : new HashMap<>();
        all.forEach((key, value) -> {
            if (belongsTo(key, serverKey)) {
                kept.put(key, value);
            }
        });
        return kept;
    }
}
