package dev.hoyin1600p.vhaccelerator.client.model;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Key-only index with backed filtered views and a safe uncached path for foreign maps. */
final class NamespaceIndex<K, V> {
    private final Map<K, V> registry;
    private final Function<K, String> namespace;
    private Map<String, List<K>> keys = Map.of();
    private long indexedVersion = -1;
    private int builds;
    private long buildNanos;

    NamespaceIndex(Map<K, V> registry, Function<K, String> namespace) {
        this.registry = registry;
        this.namespace = namespace;
    }

    private List<K> matching(String target) {
        // An unknown map may mutate through aliases; size is NOT a valid epoch.
        long version = registry instanceof StructurallyVersioned tracked
                ? tracked.structuralVersion() : -1;
        if (version < 0 || indexedVersion != version) {
            long start = System.nanoTime();
            Map<String, List<K>> next = new LinkedHashMap<>();
            for (K key : registry.keySet()) {
                next.computeIfAbsent(namespace.apply(key), ignored -> new ArrayList<>()).add(key);
            }
            keys = next;
            indexedVersion = version;
            builds++;
            buildNanos += System.nanoTime() - start;
        }
        return keys.getOrDefault(target, List.of());
    }

    int builds() { return builds; }
    long buildNanos() { return buildNanos; }
    int namespaces() { return keys.size(); }

    Set<K> keys(String target) {
        return new AbstractSet<>() {
            @Override public int size() { return matching(target).size(); }
            @SuppressWarnings("unchecked") // A present registry key is of K, as required by Map's key contract.
            @Override public boolean contains(Object key) {
                return registry.containsKey(key) && Objects.equals(target, namespace.apply((K) key));
            }
            @Override public boolean remove(Object key) {
                if (!contains(key)) { return false; }
                registry.remove(key);
                return true;
            }
            @Override public Iterator<K> iterator() {
                Iterator<K> snapshot = new ArrayList<>(matching(target)).iterator();
                return new Iterator<>() {
                    private K last;
                    private boolean removable;
                    @Override public boolean hasNext() { return snapshot.hasNext(); }
                    @Override public K next() { last = snapshot.next(); removable = true; return last; }
                    @Override public void remove() {
                        if (!removable) { throw new IllegalStateException(); }
                        registry.remove(last);
                        removable = false;
                    }
                };
            }
        };
    }

    Set<Map.Entry<K, V>> entries(String target) {
        return new AbstractSet<>() {
            @Override public int size() { return matching(target).size(); }
            @Override public Iterator<Map.Entry<K, V>> iterator() {
                Iterator<K> keys = keys(target).iterator();
                return new Iterator<>() {
                    @Override public boolean hasNext() { return keys.hasNext(); }
                    @Override public Map.Entry<K, V> next() {
                        K key = keys.next();
                        return new Map.Entry<>() {
                            @Override public K getKey() { return key; }
                            @Override public V getValue() { return registry.get(key); }
                            @Override public V setValue(V value) {
                                if (!registry.containsKey(key)) { throw new IllegalStateException("Model entry was removed"); }
                                return registry.put(key, value);
                            }
                            @Override public int hashCode() { return Objects.hashCode(key) ^ Objects.hashCode(getValue()); }
                            @Override public boolean equals(Object other) {
                                return other instanceof Map.Entry<?, ?> entry
                                        && Objects.equals(key, entry.getKey()) && Objects.equals(getValue(), entry.getValue());
                            }
                        };
                    }
                    @Override public void remove() { keys.remove(); }
                };
            }
        };
    }

    void replaceAll(Collection<String> namespaces, BiFunction<? super K, ? super V, ? extends V> replacement) {
        for (String target : new LinkedHashSet<>(namespaces)) {
            for (K key : keys(target)) {
                registry.put(key, replacement.apply(key, registry.get(key)));
            }
        }
    }
}
