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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
 *
 * <p>Reads share a read lock, so parallel eager lookups never serialize.
 * Mutations, bakes and compound view operations take the write lock. A
 * lookup that must bake releases its read lock before taking the write lock
 * and then re-evaluates the key, since the state may have changed between.
 * The bake thread holds the write lock while baking, so its reentrant
 * lookups and writes still reach the map.
 */
public final class DeferredModelRegistry<K, V> extends AbstractMap<K, V>
        implements StructurallyVersioned {
    public interface FailureListener<K> {
        void failed(K key, Throwable failure);
    }

    /** Marks a lookup that the read lock alone cannot answer. */
    private static final Object UNRESOLVED = new Object();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
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
    // Also counted by concurrent readers holding only the read lock.
    private final AtomicInteger retiredLookups = new AtomicInteger();
    private final AtomicInteger offThreadLookups = new AtomicInteger();

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
    public int size() {
        readLock.lock();
        try {
            return eager.size() + deferred.size();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        readLock.lock();
        try {
            return eager.isEmpty() && deferred.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean containsKey(Object key) {
        readLock.lock();
        try {
            return eager.containsKey(key) || deferred.contains(key);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public V get(Object key) {
        return lookup(key, null, false);
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        return lookup(key, defaultValue, true);
    }

    @SuppressWarnings("unchecked") // Only peek's own answers and UNRESOLVED.
    private V lookup(Object key, V absent, boolean absentWhileBaking) {
        Object value;
        readLock.lock();
        try {
            value = peek(key, absent, absentWhileBaking);
        } finally {
            readLock.unlock();
        }
        if (value != UNRESOLVED) {
            return (V) value;
        }
        // Never upgrade a held read lock; re-evaluate under the write lock.
        writeLock.lock();
        try {
            value = peek(key, absent, absentWhileBaking);
            return value != UNRESOLVED ? (V) value : resolve((K) key);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Answers a lookup that needs no bake, or returns {@link #UNRESOLVED}.
     * Requires either lock; only atomic counters change.
     */
    private Object peek(Object key, V absent, boolean absentWhileBaking) {
        V value = eager.get(key);
        if (value != null || eager.containsKey(key)) {
            return value;
        }
        if (!deferred.contains(key)
                || absentWhileBaking && baking.contains(key)) {
            return absent;
        }
        value = resolved.get(key);
        if (value != null || resolved.containsKey(key)) {
            return value;
        }
        if (baker == null) {
            retiredLookups.incrementAndGet();
            return fallback;
        }
        if (!bakeThread.getAsBoolean()) {
            // The bakery is not thread-safe; leave the key for the bake thread.
            offThreadLookups.incrementAndGet();
            return fallback;
        }
        return UNRESOLVED;
    }

    @Override
    public V put(K key, V value) {
        writeLock.lock();
        try {
            if (deferred.contains(key)) {
                // Map#put returns the previous logical value, which may need a bake.
                V previous = resolve(key);
                resolved.put(key, value);
                return previous;
            }
            return eager.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        writeLock.lock();
        try {
            if (eager.containsKey(key)) {
                return eager.remove(key);
            }
            if (!deferred.contains(key)) {
                return null;
            }
            V previous = resolve((K) key);
            removeDeferred(key);
            return previous;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            eager.clear();
            if (!deferred.isEmpty()) {
                deferred.clear();
                ownVersion++;
            }
            resolved.clear();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Combines this map's deferred-key removals with the eager map's own
     * version. An unversioned eager map makes the whole registry unversioned.
     */
    @Override
    public long structuralVersion() {
        readLock.lock();
        try {
            if (!(eager instanceof StructurallyVersioned versioned)) {
                return -1;
            }
            long eagerVersion = versioned.structuralVersion();
            return eagerVersion < 0 ? -1 : eagerVersion + ownVersion;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Stops all future bakes; called before the owning atlases close. Waits
     * for any bake in progress on another thread to finish first.
     */
    public void retire() {
        writeLock.lock();
        try {
            baker = null;
        } finally {
            writeLock.unlock();
        }
    }

    public boolean isRetired() {
        readLock.lock();
        try {
            return baker == null;
        } finally {
            readLock.unlock();
        }
    }

    /** True when reading {@code key} would bake a model now. */
    public boolean isUnresolvedDeferred(Object key) {
        readLock.lock();
        try {
            return baker != null
                    && deferred.contains(key)
                    && !resolved.containsKey(key);
        } finally {
            readLock.unlock();
        }
    }

    /** True for a present deferred key, whether or not it has baked. */
    public boolean isDeferred(Object key) {
        readLock.lock();
        try {
            return deferred.contains(key);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Present deferred keys whose value has never been read or replaced.
     * Constant time; resolved values are always a subset of deferred keys.
     */
    public int unresolvedDeferred() {
        readLock.lock();
        try {
            return deferred.size() - resolved.size();
        } finally {
            readLock.unlock();
        }
    }

    /** Deferred keys removed by callers since construction. */
    public int removedDeferred() {
        readLock.lock();
        try {
            return initialDeferred - deferred.size();
        } finally {
            readLock.unlock();
        }
    }

    public int initialDeferred() {
        return initialDeferred;
    }

    public int bakedOnDemand() {
        readLock.lock();
        try {
            return bakedOnDemand;
        } finally {
            readLock.unlock();
        }
    }

    public int failedBakes() {
        readLock.lock();
        try {
            return failedBakes;
        } finally {
            readLock.unlock();
        }
    }

    public int retiredLookups() {
        return retiredLookups.get();
    }

    /** Unbaked-key lookups answered with the fallback off the bake thread. */
    public int offThreadLookups() {
        return offThreadLookups.get();
    }

    /** Requires the write lock: it may bake and mutate. */
    private V resolve(K key) {
        if (resolved.containsKey(key)) {
            return resolved.get(key);
        }
        Function<? super K, ? extends V> activeBaker = baker;
        if (activeBaker == null) {
            retiredLookups.incrementAndGet();
            return fallback;
        }
        if (!bakeThread.getAsBoolean()) {
            // The bakery is not thread-safe; leave the key for the bake thread.
            offThreadLookups.incrementAndGet();
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
            writeLock.lock();
            try {
                if (!containsKey(key)) {
                    throw new IllegalStateException("Model entry was removed");
                }
                return put(key, value);
            } finally {
                writeLock.unlock();
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
            writeLock.lock();
            try {
                if (lastDeferred) {
                    deferredKeys.remove();
                    resolved.remove(last);
                    ownVersion++;
                } else {
                    eagerKeys.remove();
                }
            } finally {
                writeLock.unlock();
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
                writeLock.lock();
                try {
                    if (!containsKey(key)) {
                        return false;
                    }
                    if (eager.containsKey(key)) {
                        eager.remove(key);
                    } else {
                        removeDeferred(key);
                    }
                    return true;
                } finally {
                    writeLock.unlock();
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
                // Write lock, not read: get may bake, and a read lock cannot upgrade.
                writeLock.lock();
                try {
                    return containsKey(entry.getKey())
                            && Objects.equals(
                                    get(entry.getKey()),
                                    entry.getValue()
                            );
                } finally {
                    writeLock.unlock();
                }
            }

            @Override
            public boolean remove(Object candidate) {
                writeLock.lock();
                try {
                    if (!contains(candidate)) {
                        return false;
                    }
                    DeferredModelRegistry.this.remove(
                            ((Map.Entry<?, ?>) candidate).getKey()
                    );
                    return true;
                } finally {
                    writeLock.unlock();
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
     * leaves the item pending.
     *
     * <p>A pending item is stored in the same map as cached items, under a
     * {@link Pending} marker that the owner thread atomically replaces with
     * the baked model. {@code containsKey}, {@code size}, {@code keySet} and
     * {@code entrySet} therefore always describe one key set, even while an
     * item resolves, and enumerating them never bakes. An entry's value is
     * read live and resolves only that item. Writes and removals that replace
     * a pending item report no previous value, so they never bake either.
     * Values may be {@code null}, as in Forge's {@code HashMap}; keys may not.
     */
    public static final class ItemCache<I, L, V> extends AbstractMap<I, V> {
        private static final Object NULL = new Object();

        /** Identity-compared, so a replaced marker is never resolved twice. */
        private static final class Pending<T> {
            private final T location;

            private Pending(T location) {
                this.location = location;
            }
        }

        private final ConcurrentHashMap<I, Object> storage =
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
            // One atomic swap, so readers always see a model or the marker.
            storage.put(Objects.requireNonNull(item),
                    new Pending<>(Objects.requireNonNull(location)));
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
            return item != null && storage.get(item) instanceof Pending<?>;
        }

        /** Diagnostic scan; linear in the number of items. */
        public int pendingCount() {
            int count = 0;
            for (Object stored : storage.values()) {
                if (stored instanceof Pending<?>) {
                    count++;
                }
            }
            return count;
        }

        @Override
        @SuppressWarnings("unchecked") // Only defer creates markers.
        public V get(Object item) {
            if (item == null) {
                return null;
            }
            Object stored = storage.get(item);
            if (stored instanceof Pending<?> marker) {
                return resolve(item, (Pending<L>) marker);
            }
            return previousValue(stored);
        }

        @SuppressWarnings("unchecked") // Only accepted keys become pending.
        private V resolve(Object item, Pending<L> marker) {
            DeferredModelRegistry<L, V> source = registry;
            V model = source == null ? null : source.get(marker.location);
            if (model == null) {
                // Released for reload, a reentrant bake, or a removed key.
                return missing.get();
            }
            if (!ownerThread.getAsBoolean() || source.isRetired()) {
                return model;
            }
            // Fails if a reentrant put or defer already replaced this marker.
            storage.replace((I) item, marker, mask(model));
            return model;
        }

        @Override
        public boolean containsKey(Object item) {
            return item != null && storage.containsKey(item);
        }

        @Override
        public boolean isEmpty() {
            return storage.isEmpty();
        }

        /** A replaced pending item reports no previous value. */
        @Override
        public V put(I item, V value) {
            return previousValue(storage.put(
                    Objects.requireNonNull(item),
                    mask(value)
            ));
        }

        /** A removed pending item reports no previous value. */
        @Override
        public V remove(Object item) {
            return item == null ? null : previousValue(storage.remove(item));
        }

        @Override
        public void clear() {
            storage.clear();
        }

        @Override
        public int size() {
            return storage.size();
        }

        @Override
        public Set<I> keySet() {
            return new AbstractSet<>() {
                @Override
                public int size() {
                    return storage.size();
                }

                @Override
                public boolean isEmpty() {
                    return storage.isEmpty();
                }

                @Override
                public boolean contains(Object item) {
                    return containsKey(item);
                }

                @Override
                public boolean remove(Object item) {
                    return item != null && storage.remove(item) != null;
                }

                @Override
                public void clear() {
                    storage.clear();
                }

                @Override
                public Iterator<I> iterator() {
                    // Weakly consistent, as for any concurrent map; no bakes.
                    return storage.keySet().iterator();
                }
            };
        }

        @Override
        public Set<Map.Entry<I, V>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public int size() {
                    return storage.size();
                }

                @Override
                public boolean isEmpty() {
                    return storage.isEmpty();
                }

                @Override
                public boolean contains(Object candidate) {
                    return candidate instanceof Map.Entry<?, ?> entry
                            && containsKey(entry.getKey())
                            && Objects.equals(
                                    get(entry.getKey()),
                                    entry.getValue()
                            );
                }

                @Override
                public boolean remove(Object candidate) {
                    if (!(candidate instanceof Map.Entry<?, ?> entry)
                            || entry.getKey() == null) {
                        return false;
                    }
                    // Reads, and so may resolve, only this entry's item.
                    V current = get(entry.getKey());
                    Object stored = storage.get(entry.getKey());
                    return stored != null
                            && Objects.equals(current, entry.getValue())
                            && storage.remove(entry.getKey(), stored);
                }

                @Override
                public void clear() {
                    storage.clear();
                }

                @Override
                public Iterator<Map.Entry<I, V>> iterator() {
                    Iterator<I> keys = storage.keySet().iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return keys.hasNext();
                        }

                        @Override
                        public Map.Entry<I, V> next() {
                            return new LiveEntry(keys.next());
                        }

                        @Override
                        public void remove() {
                            keys.remove();
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

        /** Absent or pending previous values read as null, never baking. */
        private V previousValue(Object stored) {
            return stored == null || stored instanceof Pending<?>
                    ? null
                    : unmask(stored);
        }

        /**
         * Reads its item's current value on demand, so iterating entries
         * never bakes and reading one resolves only that item.
         */
        private final class LiveEntry implements Map.Entry<I, V> {
            private final I item;

            private LiveEntry(I item) {
                this.item = item;
            }

            @Override
            public I getKey() {
                return item;
            }

            @Override
            public V getValue() {
                return get(item);
            }

            /** Replaces a pending item without baking its old model. */
            @Override
            public V setValue(V value) {
                Object masked = mask(value);
                while (true) {
                    Object stored = storage.get(item);
                    if (stored == null) {
                        throw new IllegalStateException(
                                "Item model entry was removed");
                    }
                    if (storage.replace(item, stored, masked)) {
                        return previousValue(stored);
                    }
                }
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
