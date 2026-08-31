package dev.hoyin1600p.vhaccelerator.client.compat.decocraft;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

final class ReloadScopedObjectCache {
    private final ThreadLocal<String> activeKey = new ThreadLocal<>();
    private final ConcurrentMap<String, Object> values =
            new ConcurrentHashMap<>();
    private final LongAdder lookups = new LongAdder();
    private final LongAdder hits = new LongAdder();
    private final LongAdder parses = new LongAdder();

    void begin(String key) {
        if (key == null || key.isBlank()) {
            activeKey.remove();
            return;
        }
        activeKey.set(key);
    }

    Object find() {
        String key = activeKey.get();
        if (key == null) {
            return null;
        }
        lookups.increment();
        Object value = values.get(key);
        if (value != null) {
            hits.increment();
        }
        return value;
    }

    void store(Object value) {
        String key = activeKey.get();
        if (key == null || value == null) {
            return;
        }
        parses.increment();
        values.putIfAbsent(key, value);
    }

    void finish() {
        activeKey.remove();
    }

    Snapshot snapshot() {
        return new Snapshot(
                lookups.sum(),
                hits.sum(),
                parses.sum(),
                values.size()
        );
    }

    void clear() {
        activeKey.remove();
        values.clear();
        lookups.reset();
        hits.reset();
        parses.reset();
    }

    record Snapshot(long lookups, long hits, long parses, int uniqueModels) {
    }
}
