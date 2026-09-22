package dev.hoyin1600p.vhaccelerator.client.model;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Replacement for {@code BlockModelShaper}'s state-to-model lookup in which
 * states whose model has not baked yet are stored as a pending location and
 * resolve through the model manager on their first {@code get}, on whichever
 * thread asks (usually a chunk-compile worker).
 *
 * <p>Every state is present from construction, so {@code containsKey},
 * {@code size} and key iteration never bake. A resolved model replaces its
 * marker atomically, and only while {@code live} holds: after the owning
 * registry retires, a pending state reads through without caching, and the
 * reload's own rebuild replaces this map. Writes and removals that replace a
 * pending state report no previous value, so they never bake. Values may be
 * {@code null}; keys may not.
 */
public final class LazyStateModelCache<S, L, V> extends AbstractMap<S, V> {
    private static final Object NULL = new Object();

    /** Identity-compared, so a replaced marker is never resolved twice. */
    private static final class Pending<T> {
        private final T location;

        private Pending(T location) {
            this.location = location;
        }
    }

    private final ConcurrentHashMap<S, Object> storage;
    private final Function<? super L, ? extends V> resolver;
    private final BooleanSupplier live;

    public LazyStateModelCache(
            int expectedSize,
            Function<? super L, ? extends V> resolver,
            BooleanSupplier live
    ) {
        this.storage = new ConcurrentHashMap<>(Math.max(16, expectedSize));
        this.resolver = Objects.requireNonNull(resolver);
        this.live = Objects.requireNonNull(live);
    }

    /** Construction only; stores a resolved model. */
    public void putResolved(S state, V model) {
        storage.put(Objects.requireNonNull(state), mask(model));
    }

    /** Construction only; leaves the state to resolve on first read. */
    public void defer(S state, L location) {
        storage.put(Objects.requireNonNull(state),
                new Pending<>(Objects.requireNonNull(location)));
    }

    public boolean isPending(Object state) {
        return state != null && storage.get(state) instanceof Pending<?>;
    }

    /** Diagnostic scan; linear in the number of states. */
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
    public V get(Object state) {
        if (state == null) {
            return null;
        }
        Object stored = storage.get(state);
        if (stored instanceof Pending<?> marker) {
            V model = resolver.apply(((Pending<L>) marker).location);
            if (model != null && live.getAsBoolean()) {
                // Fails if a put already replaced this marker; that write wins.
                if (!storage.replace((S) state, marker, model)) {
                    return get(state);
                }
            } else if (storage.get(state) != marker) {
                // A direct write also wins when the registry retired during
                // resolution or the resolver could not return a model.
                return get(state);
            }
            return model;
        }
        return previousValue(stored);
    }

    @Override
    public boolean containsKey(Object state) {
        return state != null && storage.containsKey(state);
    }

    @Override
    public boolean isEmpty() {
        return storage.isEmpty();
    }

    @Override
    public int size() {
        return storage.size();
    }

    @Override
    public V put(S state, V value) {
        return previousValue(storage.put(Objects.requireNonNull(state), mask(value)));
    }

    @Override
    public V remove(Object state) {
        return state == null ? null : previousValue(storage.remove(state));
    }

    @Override
    public void clear() {
        storage.clear();
    }

    @Override
    public Set<Map.Entry<S, V>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return storage.size();
            }

            @Override
            public Iterator<Map.Entry<S, V>> iterator() {
                Iterator<S> keys = storage.keySet().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return keys.hasNext();
                    }

                    @Override
                    public Map.Entry<S, V> next() {
                        S state = keys.next();
                        return new LiveEntry(state);
                    }

                    @Override
                    public void remove() {
                        keys.remove();
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
                return storage.size();
            }

            @Override
            public boolean contains(Object state) {
                return containsKey(state);
            }

            @Override
            public boolean remove(Object state) {
                return state != null && storage.remove(state) != null;
            }

            @Override
            public Iterator<S> iterator() {
                return storage.keySet().iterator();
            }
        };
    }

    private static Object mask(Object value) {
        return value == null ? NULL : value;
    }

    @SuppressWarnings("unchecked")
    private V previousValue(Object stored) {
        return stored == null || stored == NULL || stored instanceof Pending<?>
                ? null
                : (V) stored;
    }

    /** Reads its state's value on demand; iteration alone never bakes. */
    private final class LiveEntry implements Map.Entry<S, V> {
        private final S state;

        private LiveEntry(S state) {
            this.state = state;
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
            Object stored = storage.replace(state, mask(value));
            if (stored == null) {
                throw new IllegalStateException("Block model entry was removed");
            }
            return previousValue(stored);
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
