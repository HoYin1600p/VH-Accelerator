package dev.hoyin1600p.vhaccelerator.client.model;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Baked-model registry whose deferred keys are logically present from the
 * start and bake on first value access.
 *
 * <p>Unlike ModernFix 1.18's provider, {@code containsKey}, {@code size},
 * {@code keySet}, and {@code entrySet} all describe the same key set: eager
 * keys plus every deferred key not explicitly removed. Key iteration never
 * bakes. Entry values bake only when read, so a callback that filters by key
 * does not force unrelated models. A deferred key never moves between the
 * backing collections, so baking or replacing its value during iteration is
 * not a structural modification, exactly as with vanilla's {@code HashMap}.
 *
 * <p>A deferred bake that throws or returns {@code null} maps the key to the
 * fallback (missing) model. Vanilla would omit such a key; callers that use
 * {@code getOrDefault(key, missing)}, as {@code ModelManager} does, observe
 * the same model. After {@link #retire()}, unbaked deferred keys also resolve
 * to the fallback and nothing is baked against a closed atlas.
 */
public final class DeferredModelRegistry<K, V> extends AbstractMap<K, V>
        implements StructurallyVersioned {
    public interface FailureListener<K> {
        void failed(K key, Throwable failure);
    }

    private final Map<K, V> eager;
    private final Set<K> deferred;
    private final Map<K, V> resolved = new HashMap<>();
    private final Set<K> baking = new HashSet<>();
    private final V fallback;
    private final FailureListener<? super K> failures;
    private final int initialDeferred;
    private Function<? super K, ? extends V> baker;
    private long ownVersion;
    private int bakedOnDemand;
    private int failedBakes;
    private int retiredLookups;

    public DeferredModelRegistry(
            Map<K, V> eager,
            Collection<? extends K> deferredKeys,
            Function<? super K, ? extends V> baker,
            V fallback,
            FailureListener<? super K> failures
    ) {
        this.eager = Objects.requireNonNull(eager);
        this.baker = Objects.requireNonNull(baker);
        this.failures = Objects.requireNonNull(failures);
        this.fallback = fallback;
        LinkedHashSet<K> keys = new LinkedHashSet<>(deferredKeys);
        // A null key is never deferred; lookups for it reach the eager map.
        keys.remove(null);
        // An eagerly baked entry always wins; the two key sets stay disjoint.
        keys.removeAll(eager.keySet());
        this.deferred = keys;
        this.initialDeferred = keys.size();
    }

    @Override
    public synchronized int size() {
        return eager.size() + deferred.size();
    }

    @Override
    public synchronized boolean isEmpty() {
        return eager.isEmpty() && deferred.isEmpty();
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        return eager.containsKey(key) || deferred.contains(key);
    }

    @Override
    @SuppressWarnings("unchecked") // Only keys accepted into the deferred set reach resolve.
    public synchronized V get(Object key) {
        V value = eager.get(key);
        if (value != null || eager.containsKey(key)) {
            return value;
        }
        if (!deferred.contains(key)) {
            return null;
        }
        return resolve((K) key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized V getOrDefault(Object key, V defaultValue) {
        V value = eager.get(key);
        if (value != null || eager.containsKey(key)) {
            return value;
        }
        if (!deferred.contains(key) || baking.contains(key)) {
            return defaultValue;
        }
        return resolve((K) key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized V put(K key, V value) {
        if (deferred.contains(key)) {
            // Map#put returns the previous logical value, which may need a bake.
            V previous = resolve(key);
            resolved.put(key, value);
            return previous;
        }
        return eager.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized V remove(Object key) {
        if (eager.containsKey(key)) {
            return eager.remove(key);
        }
        if (!deferred.contains(key)) {
            return null;
        }
        V previous = resolve((K) key);
        removeDeferred(key);
        return previous;
    }

    @Override
    public synchronized void clear() {
        eager.clear();
        if (!deferred.isEmpty()) {
            deferred.clear();
            ownVersion++;
        }
        resolved.clear();
    }

    /**
     * Combines this map's deferred-key removals with the eager map's own
     * version. An unversioned eager map makes the whole registry unversioned.
     */
    @Override
    public synchronized long structuralVersion() {
        if (!(eager instanceof StructurallyVersioned versioned)) {
            return -1;
        }
        long eagerVersion = versioned.structuralVersion();
        return eagerVersion < 0 ? -1 : eagerVersion + ownVersion;
    }

    /** Stops all future bakes; called before the owning atlases close. */
    public synchronized void retire() {
        baker = null;
    }

    public synchronized boolean isRetired() {
        return baker == null;
    }

    /** True when reading {@code key} would bake a model now. */
    public synchronized boolean isUnresolvedDeferred(Object key) {
        return baker != null
                && deferred.contains(key)
                && !resolved.containsKey(key);
    }

    /** True for a present deferred key, whether or not it has baked. */
    public synchronized boolean isDeferred(Object key) {
        return deferred.contains(key);
    }

    /**
     * Present deferred keys whose value has never been read or replaced.
     * Constant time; resolved values are always a subset of deferred keys.
     */
    public synchronized int unresolvedDeferred() {
        return deferred.size() - resolved.size();
    }

    /** Deferred keys removed by callers since construction. */
    public synchronized int removedDeferred() {
        return initialDeferred - deferred.size();
    }

    public int initialDeferred() {
        return initialDeferred;
    }

    public synchronized int bakedOnDemand() {
        return bakedOnDemand;
    }

    public synchronized int failedBakes() {
        return failedBakes;
    }

    public synchronized int retiredLookups() {
        return retiredLookups;
    }

    private V resolve(K key) {
        if (resolved.containsKey(key)) {
            return resolved.get(key);
        }
        Function<? super K, ? extends V> activeBaker = baker;
        if (activeBaker == null) {
            retiredLookups++;
            return fallback;
        }
        if (!baking.add(key)) {
            // Reentrant lookup while this key bakes: absent, as during eager baking.
            return null;
        }
        V value = null;
        Throwable failure = null;
        try {
            value = activeBaker.apply(key);
        } catch (RuntimeException | LinkageError bakeFailure) {
            failure = bakeFailure;
        } finally {
            baking.remove(key);
        }
        if (value == null) {
            failedBakes++;
            failures.failed(key, failure);
            value = fallback;
        } else {
            bakedOnDemand++;
        }
        if (resolved.containsKey(key)) {
            // A reentrant put replaced the value while it baked; the later write wins.
            return resolved.get(key);
        }
        if (deferred.contains(key)) {
            resolved.put(key, value);
        }
        return value;
    }

    private void removeDeferred(Object key) {
        deferred.remove(key);
        resolved.remove(key);
        ownVersion++;
    }

    private final class LazyEntry implements Map.Entry<K, V> {
        private final K key;

        private LazyEntry(K key) {
            this.key = key;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return get(key);
        }

        @Override
        public V setValue(V value) {
            synchronized (DeferredModelRegistry.this) {
                if (!containsKey(key)) {
                    throw new IllegalStateException("Model entry was removed");
                }
                return put(key, value);
            }
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Map.Entry<?, ?> entry
                    && Objects.equals(key, entry.getKey())
                    && Objects.equals(getValue(), entry.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(getValue());
        }

        @Override
        public String toString() {
            return key + "=" + getValue();
        }
    }

    /** Fail-fast like its backing collections; removal is supported. */
    private final class KeyIterator implements Iterator<K> {
        private final Iterator<K> eagerKeys = eager.keySet().iterator();
        private final Iterator<K> deferredKeys = deferred.iterator();
        private K last;
        private boolean lastDeferred;
        private boolean removable;

        @Override
        public boolean hasNext() {
            return eagerKeys.hasNext() || deferredKeys.hasNext();
        }

        @Override
        public K next() {
            if (eagerKeys.hasNext()) {
                last = eagerKeys.next();
                lastDeferred = false;
            } else if (deferredKeys.hasNext()) {
                last = deferredKeys.next();
                lastDeferred = true;
            } else {
                throw new NoSuchElementException();
            }
            removable = true;
            return last;
        }

        @Override
        public void remove() {
            if (!removable) {
                throw new IllegalStateException();
            }
            synchronized (DeferredModelRegistry.this) {
                if (lastDeferred) {
                    deferredKeys.remove();
                    resolved.remove(last);
                    ownVersion++;
                } else {
                    eagerKeys.remove();
                }
            }
            removable = false;
        }
    }

    @Override
    public Set<K> keySet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return DeferredModelRegistry.this.size();
            }

            @Override
            public boolean contains(Object key) {
                return containsKey(key);
            }

            @Override
            public boolean remove(Object key) {
                synchronized (DeferredModelRegistry.this) {
                    if (!containsKey(key)) {
                        return false;
                    }
                    if (eager.containsKey(key)) {
                        eager.remove(key);
                    } else {
                        removeDeferred(key);
                    }
                    return true;
                }
            }

            @Override
            public void clear() {
                DeferredModelRegistry.this.clear();
            }

            @Override
            public Iterator<K> iterator() {
                return new KeyIterator();
            }
        };
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return DeferredModelRegistry.this.size();
            }

            @Override
            public boolean contains(Object candidate) {
                if (!(candidate instanceof Map.Entry<?, ?> entry)) {
                    return false;
                }
                synchronized (DeferredModelRegistry.this) {
                    return containsKey(entry.getKey())
                            && Objects.equals(
                                    get(entry.getKey()),
                                    entry.getValue()
                            );
                }
            }

            @Override
            public boolean remove(Object candidate) {
                synchronized (DeferredModelRegistry.this) {
                    if (!contains(candidate)) {
                        return false;
                    }
                    DeferredModelRegistry.this.remove(
                            ((Map.Entry<?, ?>) candidate).getKey()
                    );
                    return true;
                }
            }

            @Override
            public void clear() {
                DeferredModelRegistry.this.clear();
            }

            @Override
            public Iterator<Map.Entry<K, V>> iterator() {
                KeyIterator keys = new KeyIterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return keys.hasNext();
                    }

                    @Override
                    public Map.Entry<K, V> next() {
                        return new LazyEntry(keys.next());
                    }

                    @Override
                    public void remove() {
                        keys.remove();
                    }
                };
            }
        };
    }
}
