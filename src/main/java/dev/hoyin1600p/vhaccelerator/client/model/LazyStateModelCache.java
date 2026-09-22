package dev.hoyin1600p.vhaccelerator.client.model;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Replacement for {@code BlockModelShaper}'s state-to-model lookup in which
 * states whose model has not baked yet are pending and resolve through the
 * model manager on their first {@code get}, on whichever thread asks
 * (usually a chunk-compile worker).
 *
 * <p>Storage is compact. The states given at construction get a private,
 * immutable open-addressed index into atomic value slots; vanilla state IDs
 * are not used, because Forge rebuilds them on every registry bake and sync.
 * A pending slot holds one shared marker and recomputes its location from
 * the state when read, so no per-state marker or location is retained. Any
 * other key (an unknown or unregistered state) lives in an overflow
 * {@link ConcurrentHashMap} with ordinary map semantics.
 *
 * <p>Every construction state is present until removed, so
 * {@code containsKey}, {@code size} and key iteration never bake. A resolved
 * model replaces the marker atomically, and only when {@code cacheable}
 * accepts its location: after the owning registry retires, or when the
 * registry did not publish the value, a pending state reads through without
 * caching, and the reload's own rebuild replaces or clears this map. The
 * marker is never reinstalled, so a direct write or removal during a
 * resolution always wins. Writes and removals that replace a pending state
 * report no previous value, so they never bake. Values may be {@code null};
 * keys may not. Iteration is weakly consistent, like
 * {@code ConcurrentHashMap}'s.
 */
public final class LazyStateModelCache<S, L, V> extends AbstractMap<S, V> {
    private static final Object NULL = new Object();
    /** Present; resolves on first read. Only the constructor writes it. */
    private static final Object PENDING = new Object();
    /** A construction state that was removed. */
    private static final Object ABSENT = new Object();

    private final Object[] keys;
    /** Position + 1 of the key hashed to each slot; 0 is empty. */
    private final int[] index;
    private final AtomicReferenceArray<Object> values;
    private final AtomicInteger present;
    private final ConcurrentHashMap<S, Object> overflow =
            new ConcurrentHashMap<>();
    private final Function<? super S, ? extends L> locator;
    private final Function<? super L, ? extends V> resolver;
    private final Predicate<? super L> cacheable;

    /**
     * Every non-null state starts pending; duplicates keep one position.
     * {@code cacheable} decides whether a resolved model may be kept.
     */
    public LazyStateModelCache(
            Collection<? extends S> states,
            Function<? super S, ? extends L> locator,
            Function<? super L, ? extends V> resolver,
            Predicate<? super L> cacheable
    ) {
        this.locator = Objects.requireNonNull(locator);
        this.resolver = Objects.requireNonNull(resolver);
        this.cacheable = Objects.requireNonNull(cacheable);
        int[] table = new int[tableCapacity(states.size())];
        Object[] ordered = new Object[states.size()];
        int count = 0;
        for (S state : states) {
            if (state == null) {
                continue;
            }
            int slot = slotFor(state, table, ordered);
            if (table[slot] == 0) {
                ordered[count] = state;
                table[slot] = ++count;
            }
        }
        this.keys = count == ordered.length
                ? ordered
                : Arrays.copyOf(ordered, count);
        this.index = table;
        this.values = new AtomicReferenceArray<>(count);
        for (int position = 0; position < count; position++) {
            values.set(position, PENDING);
        }
        this.present = new AtomicInteger(count);
    }

    /** Smallest power of two holding {@code size} keys at most 3/4 full. */
    private static int tableCapacity(int size) {
        int capacity = 4;
        while ((long) capacity * 3 / 4 < size) {
            capacity <<= 1;
        }
        return capacity;
    }

    private static int spread(int hash) {
        int mixed = hash * 0x9E3779B9;
        return mixed ^ (mixed >>> 16);
    }

    /** The key's slot, or the empty slot where it would go. */
    private static int slotFor(Object key, int[] table, Object[] ordered) {
        int mask = table.length - 1;
        int slot = spread(key.hashCode()) & mask;
        while (true) {
            int entry = table[slot];
            if (entry == 0) {
                return slot;
            }
            Object candidate = ordered[entry - 1];
            if (candidate == key || candidate.equals(key)) {
                return slot;
            }
            slot = (slot + 1) & mask;
        }
    }

    /** Construction position of {@code key}, or -1 for any other key. */
    private int position(Object key) {
        if (key == null) {
            return -1;
        }
        int entry = index[slotFor(key, index, keys)];
        return entry - 1;
    }

    /** Number of construction positions, including removed ones. */
    public int fixedSize() {
        return keys.length;
    }

    @SuppressWarnings("unchecked")
    public S keyAt(int position) {
        return (S) keys[position];
    }

    /**
     * Stores a resolved model at a still-pending construction position.
     * Returns false, changing nothing, if the position was already written
     * or removed. Safe from any thread.
     */
    public boolean resolveAt(int position, V model) {
        return values.compareAndSet(position, PENDING, mask(model));
    }

    public boolean isPending(Object state) {
        int position = position(state);
        return position >= 0 && values.get(position) == PENDING;
    }

    /** Diagnostic scan; linear in the number of states. */
    public int pendingCount() {
        int count = 0;
        for (int position = 0; position < keys.length; position++) {
            if (values.get(position) == PENDING) {
                count++;
            }
        }
        return count;
    }

    /**
     * Approximate retained bytes, assuming compressed references and
     * 16-byte array headers; overflow entries are counted at 48 bytes.
     */
    public long estimatedBytes() {
        long arrays = 16L + 4L * keys.length // keys
                + 16L + 4L * index.length    // index
                + 32L + 4L * keys.length;    // atomic array and its slots
        return arrays + 48L * overflow.size() + 96L;
    }

    @Override
    public V get(Object state) {
        int position = position(state);
        if (position < 0) {
            return state == null ? null : valueOf(overflow.get(state));
        }
        Object stored = values.get(position);
        return stored == PENDING ? resolve(position) : valueOf(stored);
    }

    private V resolve(int position) {
        L location = locator.apply(keyAt(position));
        V model = resolver.apply(location);
        if (model != null && cacheable.test(location)) {
            if (values.compareAndSet(position, PENDING, model)) {
                return model;
            }
        } else if (values.get(position) == PENDING) {
            // Retired, unpublished or no model: read through, uncached.
            return model;
        }
        // A direct write or removal replaced the marker; that write wins.
        // The marker is never reinstalled, so this read is final.
        return valueOf(values.get(position));
    }

    @Override
    public boolean containsKey(Object state) {
        int position = position(state);
        if (position < 0) {
            return state != null && overflow.containsKey(state);
        }
        return values.get(position) != ABSENT;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public int size() {
        return present.get() + overflow.size();
    }

    @Override
    public V put(S state, V value) {
        int position = position(Objects.requireNonNull(state));
        if (position < 0) {
            return valueOf(overflow.put(state, mask(value)));
        }
        Object previous = values.getAndSet(position, mask(value));
        if (previous == ABSENT) {
            present.incrementAndGet();
        }
        return valueOf(previous);
    }

    @Override
    public V remove(Object state) {
        int position = position(state);
        if (position < 0) {
            return state == null ? null : valueOf(overflow.remove(state));
        }
        Object previous = removeAt(position);
        return previous == ABSENT ? null : valueOf(previous);
    }

    /** Returns the previous slot content, {@code ABSENT} if none. */
    private Object removeAt(int position) {
        Object previous = values.getAndSet(position, ABSENT);
        if (previous != ABSENT) {
            present.decrementAndGet();
        }
        return previous;
    }

    @Override
    public void clear() {
        for (int position = 0; position < keys.length; position++) {
            if (values.get(position) != ABSENT) {
                removeAt(position);
            }
        }
        overflow.clear();
    }

    @Override
    public Set<Map.Entry<S, V>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return LazyStateModelCache.this.size();
            }

            @Override
            public boolean contains(Object candidate) {
                // Resolves only the named state, never the whole map.
                return candidate instanceof Map.Entry<?, ?> entry
                        && containsKey(entry.getKey())
                        && Objects.equals(get(entry.getKey()), entry.getValue());
            }

            @Override
            public boolean remove(Object candidate) {
                if (!contains(candidate)) {
                    return false;
                }
                LazyStateModelCache.this.remove(((Map.Entry<?, ?>) candidate).getKey());
                return true;
            }

            @Override
            public void clear() {
                LazyStateModelCache.this.clear();
            }

            @Override
            public Iterator<Map.Entry<S, V>> iterator() {
                KeyIterator states = new KeyIterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return states.hasNext();
                    }

                    @Override
                    public Map.Entry<S, V> next() {
                        S state = states.next();
                        return new LiveEntry(state, states.lastPosition);
                    }

                    @Override
                    public void remove() {
                        states.remove();
                    }
                };
            }
        };
    }

    @Override
    public Set<S> keySet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return LazyStateModelCache.this.size();
            }

            @Override
            public boolean contains(Object state) {
                return containsKey(state);
            }

            @Override
            public boolean remove(Object state) {
                int position = position(state);
                if (position < 0) {
                    return state != null && overflow.remove(state) != null;
                }
                return removeAt(position) != ABSENT;
            }

            @Override
            public void clear() {
                LazyStateModelCache.this.clear();
            }

            @Override
            public Iterator<S> iterator() {
                return new KeyIterator();
            }
        };
    }

    private static Object mask(Object value) {
        return value == null ? NULL : value;
    }

    /** The logical value of a slot; pending reports none, so never bakes. */
    @SuppressWarnings("unchecked")
    private V valueOf(Object stored) {
        return stored == null || stored == NULL || stored == PENDING
                || stored == ABSENT
                ? null
                : (V) stored;
    }

    /**
     * Construction states still present, then overflow keys. Weakly
     * consistent; never throws {@code ConcurrentModificationException}.
     */
    private final class KeyIterator implements Iterator<S> {
        private final Iterator<S> extra = overflow.keySet().iterator();
        private int cursor = -1;
        private int nextPosition = -2;
        private int lastPosition = -1;
        private S last;

        private int findNext() {
            if (nextPosition == -2) {
                nextPosition = -1;
                while (++cursor < keys.length) {
                    if (values.get(cursor) != ABSENT) {
                        nextPosition = cursor;
                        break;
                    }
                }
            }
            return nextPosition;
        }

        @Override
        public boolean hasNext() {
            return findNext() >= 0 || extra.hasNext();
        }

        @Override
        public S next() {
            int position = findNext();
            if (position >= 0) {
                nextPosition = -2;
                lastPosition = position;
                last = keyAt(position);
                return last;
            }
            if (!extra.hasNext()) {
                throw new NoSuchElementException();
            }
            lastPosition = -1;
            last = extra.next();
            return last;
        }

        @Override
        public void remove() {
            if (last == null) {
                throw new IllegalStateException();
            }
            if (lastPosition >= 0) {
                removeAt(lastPosition);
            } else {
                extra.remove();
            }
            last = null;
        }
    }

    /** Reads its state's value on demand; iteration alone never bakes. */
    private final class LiveEntry implements Map.Entry<S, V> {
        private final S state;
        /** Construction position, or -1 for an overflow key. */
        private final int position;

        private LiveEntry(S state, int position) {
            this.state = state;
            this.position = position;
        }

        @Override
        public S getKey() {
            return state;
        }

        @Override
        public V getValue() {
            return get(state);
        }

        @Override
        public V setValue(V value) {
            Object replacement = mask(value);
            if (position < 0) {
                Object stored = overflow.replace(state, replacement);
                if (stored == null) {
                    throw new IllegalStateException("Block model entry was removed");
                }
                return valueOf(stored);
            }
            while (true) {
                Object stored = values.get(position);
                if (stored == ABSENT) {
                    throw new IllegalStateException("Block model entry was removed");
                }
                if (values.compareAndSet(position, stored, replacement)) {
                    return valueOf(stored);
                }
            }
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Map.Entry<?, ?> that
                    && Objects.equals(state, that.getKey())
                    && Objects.equals(getValue(), that.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(state) ^ Objects.hashCode(getValue());
        }
    }
}
