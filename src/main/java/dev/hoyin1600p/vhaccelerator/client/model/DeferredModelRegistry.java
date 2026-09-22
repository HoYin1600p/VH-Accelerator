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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

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
 *
 * <p>Bakes run only on the bake thread, as vanilla bakes only on the render
 * thread. A lookup of an unbaked key from any other thread returns the
 * fallback without baking or caching it, so a later bake-thread lookup still
 * bakes the real model.
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
    private final BooleanSupplier bakeThread;
    private final int initialDeferred;
    private Function<? super K, ? extends V> baker;
    private long ownVersion;
    private int bakedOnDemand;
    private int failedBakes;
    private int retiredLookups;
    private int offThreadLookups;

    public DeferredModelRegistry(
            Map<K, V> eager,
            Collection<? extends K> deferredKeys,
            Function<? super K, ? extends V> baker,
            V fallback,
            FailureListener<? super K> failures
    ) {
        this(eager, deferredKeys, baker, fallback, failures, () -> true);
    }

    public DeferredModelRegistry(
            Map<K, V> eager,
            Collection<? extends K> deferredKeys,
            Function<? super K, ? extends V> baker,
            V fallback,
            FailureListener<? super K> failures,
            BooleanSupplier bakeThread
    ) {
        this.eager = Objects.requireNonNull(eager);
        this.baker = Objects.requireNonNull(baker);
        this.failures = Objects.requireNonNull(failures);
        this.bakeThread = Objects.requireNonNull(bakeThread);
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

    /** Unbaked-key lookups answered with the fallback off the bake thread. */
    public synchronized int offThreadLookups() {
        return offThreadLookups;
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
        if (!bakeThread.getAsBoolean()) {
            // The bakery is not thread-safe; leave the key for the bake thread.
            offThreadLookups++;
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

    /**
     * Replacement for Forge's per-item baked-model cache that resolves items
     * whose model was left for a deferred bake on first {@code get}, so every
     * lookup path, including a direct {@code getItemModel(Item)}, sees a real
     * model.
     *
     * <p>Storage is concurrent so off-thread readers never observe a torn
     * table while the owner (render) thread adds entries. Only the owner
     * thread caches a resolved item; another thread receives the registry's
     * value, which is the fallback when the model has not baked yet, and
     * leaves the item pending. Pending items are present for
     * {@code containsKey} but are not iterated and report no previous value on
     * {@code put}, so neither ever bakes. Values may be {@code null}, as in
     * Forge's {@code HashMap}; keys may not.
     */
    public static final class ItemCache<I, L, V> extends AbstractMap<I, V> {
        private static final Object NULL = new Object();

        private final ConcurrentHashMap<I, Object> storage =
                new ConcurrentHashMap<>();
        private final ConcurrentHashMap<I, L> pending =
                new ConcurrentHashMap<>();
        private final Supplier<? extends V> missing;
        private final BooleanSupplier ownerThread;
        private volatile DeferredModelRegistry<L, V> registry;

        public ItemCache(
                Map<? extends I, ? extends V> initial,
                Supplier<? extends V> missing,
                BooleanSupplier ownerThread
        ) {
            this.missing = Objects.requireNonNull(missing);
            this.ownerThread = Objects.requireNonNull(ownerThread);
            for (Map.Entry<? extends I, ? extends V> entry : initial.entrySet()) {
                if (entry.getKey() != null) {
                    storage.put(entry.getKey(), mask(entry.getValue()));
                }
            }
        }

        /**
         * Starts a reload's binding; owner thread only. The caller then puts
         * or defers every item, so earlier pending items are not cleared
         * first and readers never see an item vanish mid-rebuild.
         */
        public void bind(DeferredModelRegistry<L, V> registry) {
            this.registry = registry;
        }

        /** Drops any cached model for {@code item} until it is next read. */
        public void defer(I item, L location) {
            // Pend before dropping the old model so readers always see one.
            pending.put(Objects.requireNonNull(item),
                    Objects.requireNonNull(location));
            storage.remove(item);
        }

        /**
         * Detaches a retired registry. Pending items read as the missing
         * model, uncached, until the reload's own rebuild replaces them.
         */
        public void release() {
            registry = null;
        }

        public boolean isBound() {
            return registry != null;
        }

        public boolean isPending(Object item) {
            return item != null && pending.containsKey(item);
        }

        public int pendingCount() {
            return pending.size();
        }

        @Override
        public V get(Object item) {
            if (item == null) {
                return null;
            }
            Object stored = storage.get(item);
            if (stored != null) {
                return unmask(stored);
            }
            L location = pending.get(item);
            if (location == null) {
                // The owner thread may have cached it since the first read.
                stored = storage.get(item);
                return stored == null ? null : unmask(stored);
            }
            return resolve(item, location);
        }

        @SuppressWarnings("unchecked") // Only accepted keys become pending.
        private V resolve(Object item, L location) {
            DeferredModelRegistry<L, V> source = registry;
            V model = source == null ? null : source.get(location);
            if (model == null) {
                // Released for reload, a reentrant bake, or a removed key.
                return missing.get();
            }
            if (!ownerThread.getAsBoolean() || source.isRetired()) {
                return model;
            }
            // A reentrant put during the bake already replaced this item.
            // Store before unpending so concurrent readers always see one.
            if (location.equals(pending.get(item))) {
                storage.put((I) item, mask(model));
                pending.remove(item, location);
            }
            return model;
        }

        @Override
        public boolean containsKey(Object item) {
            return item != null
                    && (storage.containsKey(item) || pending.containsKey(item));
        }

        @Override
        public V put(I item, V value) {
            Object previous = storage.put(
                    Objects.requireNonNull(item),
                    mask(value)
            );
            pending.remove(item);
            return previous == null ? null : unmask(previous);
        }

        @Override
        public V remove(Object item) {
            if (item == null) {
                return null;
            }
            pending.remove(item);
            Object previous = storage.remove(item);
            return previous == null ? null : unmask(previous);
        }

        @Override
        public void clear() {
            pending.clear();
            storage.clear();
        }

        @Override
        public int size() {
            return storage.size();
        }

        @Override
        public Set<Map.Entry<I, V>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public int size() {
                    return storage.size();
                }

                @Override
                public Iterator<Map.Entry<I, V>> iterator() {
                    Iterator<Map.Entry<I, Object>> entries =
                            storage.entrySet().iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return entries.hasNext();
                        }

                        @Override
                        public Map.Entry<I, V> next() {
                            return new CachedEntry(entries.next());
                        }

                        @Override
                        public void remove() {
                            entries.remove();
                        }
                    };
                }
            };
        }

        private static Object mask(Object value) {
            return value == null ? NULL : value;
        }

        @SuppressWarnings("unchecked")
        private V unmask(Object value) {
            return value == NULL ? null : (V) value;
        }

        private final class CachedEntry implements Map.Entry<I, V> {
            private final Map.Entry<I, Object> entry;

            private CachedEntry(Map.Entry<I, Object> entry) {
                this.entry = entry;
            }

            @Override
            public I getKey() {
                return entry.getKey();
            }

            @Override
            public V getValue() {
                return unmask(entry.getValue());
            }

            @Override
            public V setValue(V value) {
                return unmask(entry.setValue(mask(value)));
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof Map.Entry<?, ?> that
                        && Objects.equals(getKey(), that.getKey())
                        && Objects.equals(getValue(), that.getValue());
            }

            @Override
            public int hashCode() {
                return Objects.hashCode(getKey())
                        ^ Objects.hashCode(getValue());
            }

            @Override
            public String toString() {
                return getKey() + "=" + getValue();
            }
        }
    }
}
