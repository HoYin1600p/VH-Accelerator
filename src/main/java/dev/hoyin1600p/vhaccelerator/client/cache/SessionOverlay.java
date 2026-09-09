package dev.hoyin1600p.vhaccelerator.client.cache;

import java.util.HashMap;
import java.util.Map;

/** Thread-confined additions over an immutable restored snapshot. */
final class SessionOverlay<K, V> {
    private final Map<K, V> base;
    private final Map<K, V> added = new HashMap<>();
    private final boolean overlay;
    private int extraKeys;

    SessionOverlay(Map<K, V> immutableBase) { this(immutableBase, true); }
    SessionOverlay(Map<K, V> base, boolean overlay) { this.base = base; this.overlay = overlay; }
    V get(K key) { return overlay && added.containsKey(key) ? added.get(key) : base.get(key); }
    boolean containsKey(K key) { return (overlay && added.containsKey(key)) || base.containsKey(key); }
    int size() { return base.size() + extraKeys; }
    void put(K key, V value) {
        // Preserve HashMap.put semantics if model-loader callbacks reenter
        // capture between its containsKey check and publication.
        if (overlay) {
            if (!added.containsKey(key) && !base.containsKey(key)) { extraKeys++; }
            added.put(key, value);
        } else { base.put(key, value); }
    }
    Map<K, V> snapshot() {
        if (!overlay) { return Map.copyOf(base); }
        if (added.isEmpty()) { return base; }
        var merged = new HashMap<>(base);
        merged.putAll(added);
        return Map.copyOf(merged);
    }
}
