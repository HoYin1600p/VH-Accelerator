package dev.hoyin1600p.vhaccelerator.client.model;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Owns the original, newly allocated model map before it escapes its constructor.
 * All public mutation routes, including view iterators and Map default methods,
 * reach this wrapper. Replacing a value does not invalidate the key index.
 */
public final class MutationTrackingMap<K, V> extends AbstractMap<K, V>
        implements StructurallyVersioned {
    private Map<K, V> delegate;
    private long structuralVersion;

    private MutationTrackingMap(Map<K, V> freshDelegate) { delegate = freshDelegate; }

    /** Only take ownership at allocation, never around an already exposed map. */
    public static <K, V> MutationTrackingMap<K, V> ownFreshMap(Map<K, V> map) {
        return new MutationTrackingMap<>(map);
    }

    @Override public long structuralVersion() { return structuralVersion; }

    /** Preserve the owned map identity while pre-sizing its still-empty vanilla backing map. */
    public boolean reserveIfEmpty(int capacity) {
        if (!delegate.isEmpty() || delegate.getClass() != HashMap.class) { return false; }
        delegate = new HashMap<>(capacity);
        return true;
    }
    @Override public int size() { return delegate.size(); }
    @Override public V get(Object key) { return delegate.get(key); }
    @Override public boolean containsKey(Object key) { return delegate.containsKey(key); }
    @Override public V put(K key, V value) {
        int size = delegate.size();
        V previous = delegate.put(key, value);
        if (size != delegate.size()) { structuralVersion++; }
        return previous;
    }
    @Override public V remove(Object key) {
        int size = delegate.size();
        V previous = delegate.remove(key);
        if (size != delegate.size()) { structuralVersion++; }
        return previous;
    }
    @Override public void clear() {
        if (!delegate.isEmpty()) { delegate.clear(); structuralVersion++; }
    }
    @Override public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return delegate.size(); }
            @Override public void clear() { MutationTrackingMap.this.clear(); }
            @Override public Iterator<Entry<K, V>> iterator() {
                Iterator<Entry<K, V>> iterator = delegate.entrySet().iterator();
                return new Iterator<>() {
                    @Override public boolean hasNext() { return iterator.hasNext(); }
                    @Override public Entry<K, V> next() { return iterator.next(); }
                    @Override public void remove() { iterator.remove(); structuralVersion++; }
                };
            }
        };
    }
}
