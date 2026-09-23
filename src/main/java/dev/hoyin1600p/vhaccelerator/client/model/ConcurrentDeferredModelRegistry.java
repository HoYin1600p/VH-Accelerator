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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * Baked-model registry whose deferred keys are logically present from the
 * start and bake on first value access from any thread.
 *
 * <p>Key semantics match {@link DeferredModelRegistry}: {@code containsKey},
 * {@code size}, {@code keySet} and {@code entrySet} all describe eager keys
 * plus every deferred key not explicitly removed, and key iteration never
 * bakes. A deferred key never moves between the backing collections, so a
 * bake publishing its value is not a structural modification.
 *
 * <p>Unlike {@link DeferredModelRegistry}, bakes are not confined to one
 * thread. The baker must be safe to call concurrently for different keys;
 * the block-state stage only defers graphs that are fully loaded and
 * parent-bound, so a bake only reads the bakery's unbaked cache, as the
 * parallel top-level bake already does. Concurrent readers of one key wait
 * for a single bake. The structure lock is never held while baking.
 *
 * <p>A bake that throws or returns {@code null} maps the key to the fallback
 * (missing) model, as {@code ModelManager#getModel} would observe for a key
 * vanilla failed to bake. After {@link #retire()}, which waits for bakes in
 * progress, unbaked keys read as the fallback without caching it.
 * An uncaught fatal error is propagated to all readers of that bake rather
 * than silently completing waiting readers with the fallback.
 */
public final class ConcurrentDeferredModelRegistry<K, V> extends AbstractMap<K, V>
        implements StructurallyVersioned {
    public interface BakeListener<K> {
        void baked(K key, long nanos, boolean failed, Throwable failure);
    }

    private final ReentrantReadWriteLock structure = new ReentrantReadWriteLock();
    private final Lock readLock = structure.readLock();
    private final Lock writeLock = structure.writeLock();
    /** Bakes hold the read side; retirement takes the write side. */
    private final ReentrantReadWriteLock gate = new ReentrantReadWriteLock();
    private final Map<K, V> eager;
    private final Set<K> deferred;
    private final Map<K, V> resolved = new HashMap<>();
    private final ConcurrentHashMap<K, CompletableFuture<V>> inFlight =
            new ConcurrentHashMap<>();
    private final ThreadLocal<Set<Object>> bakingHere =
            ThreadLocal.withInitial(HashSet::new);
    private final V fallback;
    private final BakeListener<? super K> listener;
    private final int initialDeferred;
    private volatile Function<? super K, ? extends V> baker;
    private long ownVersion;
    private final AtomicInteger bakedOnDemand = new AtomicInteger();
    private final AtomicInteger failedBakes = new AtomicInteger();
    private final AtomicInteger retiredLookups = new AtomicInteger();
    private final AtomicInteger sharedWaits = new AtomicInteger();

    public ConcurrentDeferredModelRegistry(
            Map<K, V> eager,
            Collection<? extends K> deferredKeys,
            Function<? super K, ? extends V> baker,
            V fallback,
            BakeListener<? super K> listener
    ) {
        this.eager = Objects.requireNonNull(eager);
        this.baker = Objects.requireNonNull(baker);
        this.listener = Objects.requireNonNull(listener);
        this.fallback = fallback;
        LinkedHashSet<K> keys = new LinkedHashSet<>(deferredKeys);
        keys.remove(null);
        // An eagerly baked entry always wins; the two key sets stay disjoint.
        keys.removeIf(eager::containsKey);
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
        return lookup(key, null);
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        return lookup(key, defaultValue);
    }

    @SuppressWarnings("unchecked")
    private V lookup(Object key, V absent) {
        readLock.lock();
        try {
            V value = eager.get(key);
            if (value != null || eager.containsKey(key)) {
                return value;
            }
            if (!deferred.contains(key)) {
                return absent;
            }
            value = resolved.get(key);
            if (value != null || resolved.containsKey(key)) {
                return value;
            }
        } finally {
            readLock.unlock();
        }
        if (bakingHere.get().contains(key)) {
            // Reentrant lookup while this thread bakes it: absent, as vanilla.
            return absent;
        }
        return bakeShared((K) key);
    }

    /** Bakes {@code key} once; other threads asking for it wait. */
    private V bakeShared(K key) {
        CompletableFuture<V> mine = new CompletableFuture<>();
        CompletableFuture<V> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            sharedWaits.incrementAndGet();
            try {
                return running.join();
            } catch (CompletionException wrapped) {
                Throwable failure = wrapped.getCause();
                if (failure instanceof Error error) {
                    throw error;
                }
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw wrapped;
            }
        }
        V result = fallback;
        Throwable escaped = null;
        try {
            // A previous bake may have published and removed its future after
            // lookup checked resolved but before this future was installed.
            // Recheck ownership before starting another expensive bake.
            readLock.lock();
            try {
                if (resolved.containsKey(key)) {
                    result = resolved.get(key);
                    return result;
                }
                if (!deferred.contains(key)) {
                    result = eager.getOrDefault(key, fallback);
                    return result;
                }
            } finally {
                readLock.unlock();
            }
            result = bakeAndPublish(key);
        } catch (Throwable failure) {
            escaped = failure;
            throw failure;
        } finally {
            if (escaped == null) {
                mine.complete(result);
            } else {
                mine.completeExceptionally(escaped);
            }
            inFlight.remove(key, mine);
        }
        return result;
    }

    private V bakeAndPublish(K key) {
        V value;
        gate.readLock().lock();
        try {
            Function<? super K, ? extends V> activeBaker = baker;
            if (activeBaker == null) {
                retiredLookups.incrementAndGet();
                return fallback;
            }
            Set<Object> here = bakingHere.get();
            here.add(key);
            Throwable failure = null;
            long started = System.nanoTime();
            value = null;
            try {
                value = activeBaker.apply(key);
            } catch (RuntimeException | LinkageError bakeFailure) {
                failure = bakeFailure;
            } finally {
                here.remove(key);
            }
            boolean failed = value == null;
            if (failed) {
                failedBakes.incrementAndGet();
                value = fallback;
            } else {
                bakedOnDemand.incrementAndGet();
            }
            listener.baked(key, System.nanoTime() - started, failed, failure);
            // Retirement must wait for publication as well as baking. An
            // atlas cannot close while this result is still being installed.
            writeLock.lock();
            try {
                if (resolved.containsKey(key)) {
                    // A put replaced the value while it baked; the later write wins.
                    return resolved.get(key);
                }
                if (deferred.contains(key)) {
                    resolved.put(key, value);
                }
                return value;
            } finally {
                writeLock.unlock();
            }
        } finally {
            gate.readLock().unlock();
        }
    }

    /** True when the key is deferred, unresolved, and the registry is live. */
    private boolean needsBake(Object key) {
        return baker != null
                && deferred.contains(key)
                && !resolved.containsKey(key)
                && !eager.containsKey(key);
    }

    @Override
    public V put(K key, V value) {
        while (true) {
            writeLock.lock();
            try {
                if (!needsBake(key)) {
                    if (!deferred.contains(key)) {
                        return eager.put(key, value);
                    }
                    V previous = resolved.containsKey(key)
                            ? resolved.get(key)
                            : fallback;
                    resolved.put(key, value);
                    return previous;
                }
            } finally {
                writeLock.unlock();
            }
            // Map#put returns the previous logical value, which needs a bake.
            get(key);
            if (bakingHere.get().contains(key)) {
                return putWhileBaking(key, value);
            }
        }
    }

    /** A reentrant put during this thread's own bake of the key. */
    private V putWhileBaking(K key, V value) {
        writeLock.lock();
        try {
            if (deferred.contains(key)) {
                resolved.put(key, value);
            }
            return null;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public V remove(Object key) {
        while (true) {
            writeLock.lock();
            try {
                if (eager.containsKey(key)) {
                    return eager.remove(key);
                }
                if (!deferred.contains(key)) {
                    return null;
                }
                if (!needsBake(key) || bakingHere.get().contains(key)) {
                    V previous = resolved.containsKey(key)
                            ? resolved.get(key)
                            : bakingHere.get().contains(key) ? null : fallback;
                    removeDeferred(key);
                    return previous;
                }
            } finally {
                writeLock.unlock();
            }
            get(key); // Map#remove returns the previous logical value.
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
     * for bakes in progress on any thread to finish first.
     */
    public void retire() {
        gate.writeLock().lock();
        try {
            baker = null;
        } finally {
            gate.writeLock().unlock();
        }
    }

    public boolean isRetired() {
        return baker == null;
    }

    /** True when reading {@code key} would bake a model now. */
    public boolean isUnresolvedDeferred(Object key) {
        readLock.lock();
        try {
            return needsBake(key);
        } finally {
            readLock.unlock();
        }
    }

    public boolean isDeferred(Object key) {
        readLock.lock();
        try {
            return deferred.contains(key);
        } finally {
            readLock.unlock();
        }
    }

    public int unresolvedDeferred() {
        readLock.lock();
        try {
            return deferred.size() - resolved.size();
        } finally {
            readLock.unlock();
        }
    }

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
        return bakedOnDemand.get();
    }

    public int failedBakes() {
        return failedBakes.get();
    }

    public int retiredLookups() {
        return retiredLookups.get();
    }

    /** Lookups that waited for another thread's bake of the same key. */
    public int sharedWaits() {
        return sharedWaits.get();
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
            if (!containsKey(key)) {
                throw new IllegalStateException("Model entry was removed");
            }
            return put(key, value);
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
                return ConcurrentDeferredModelRegistry.this.size();
            }

            @Override
            public boolean contains(Object key) {
                return containsKey(key);
            }

            @Override
            public boolean remove(Object key) {
                writeLock.lock();
                try {
                    if (eager.containsKey(key)) {
                        eager.remove(key);
                        return true;
                    }
                    if (deferred.contains(key)) {
                        removeDeferred(key);
                        return true;
                    }
                    return false;
                } finally {
                    writeLock.unlock();
                }
            }

            @Override
            public void clear() {
                ConcurrentDeferredModelRegistry.this.clear();
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
                return ConcurrentDeferredModelRegistry.this.size();
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
                if (!contains(candidate)) {
                    return false;
                }
                ConcurrentDeferredModelRegistry.this.remove(
                        ((Map.Entry<?, ?>) candidate).getKey()
                );
                return true;
            }

            @Override
            public void clear() {
                ConcurrentDeferredModelRegistry.this.clear();
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
