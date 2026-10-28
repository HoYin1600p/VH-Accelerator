package dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Observes a fresh CT map without replacing its native iteration or mutation algorithms. */
public final class TrackedRecipeMap<K, V> extends AbstractMap<K, V> {
    private final HashMap<K, V> delegate;
    private final Thread owner = Thread.currentThread();
    private volatile boolean foreignMutation;
    private final List<BiConsumer<K, Boolean>> observers = new ArrayList<>();
    private final List<Runnable> orderObservers = new java.util.concurrent.CopyOnWriteArrayList<>();
    private int callbacks;
    private long structure;

    public TrackedRecipeMap(HashMap<K, V> fresh) {
        if (fresh.getClass() != HashMap.class) throw new IllegalArgumentException("An exact fresh HashMap is required");
        delegate = fresh;
    }

    void observe(BiConsumer<K, Boolean> observer) { observers.add(observer); }
    void unobserve(BiConsumer<K, Boolean> observer) { observers.remove(observer); }
    void observeOrder(Runnable observer) { orderObservers.add(observer); }
    void unobserveOrder(Runnable observer) { orderObservers.remove(observer); }
    int observerCount() { return observers.size() + orderObservers.size(); }
    boolean eligible() { return Thread.currentThread() == owner && !foreignMutation; }
    boolean busy() { return callbacks != 0; }
    long structure() { return structure; }
    Set<Entry<K, V>> nativeEntries() { return delegate.entrySet(); }
    Iterator<K> nativeToken() { return delegate.keySet().iterator(); }
    void nativeIteratorRemoved(K key) { changed(key, true); }
    private void beforePossibleResize() { orderObservers.forEach(Runnable::run); }

    private void changed(K key, boolean structural) {
        if (Thread.currentThread() != owner) { foreignMutation = true; return; }
        if (structural) structure++;
        // Observers only journal IDs; no recipe or script callbacks run in a map mutator.
        for (var observer : List.copyOf(observers)) observer.accept(key, structural);
    }

    private record State<V>(boolean present, V value) { }
    private State<V> state(Object key) { return new State<>(delegate.containsKey(key), delegate.get(key)); }
    private void reconcile(K key, State<V> before) {
        State<V> after = state(key);
        if (before.present != after.present || before.value != after.value) changed(key, before.present != after.present);
    }

    private <R> R single(K key, java.util.function.Supplier<R> operation) {
        if (Thread.currentThread() != owner) { foreignMutation = true; return operation.get(); }
        State<V> before = state(key);
        callbacks++;
        try { return operation.get(); }
        finally { callbacks--; reconcile(key, before); }
    }

    private void bulk(Runnable operation) {
        if (Thread.currentThread() != owner) { foreignMutation = true; operation.run(); return; }
        Map<K, V> before = new LinkedHashMap<>(delegate);
        callbacks++;
        try { operation.run(); }
        finally {
            callbacks--;
            for (var entry : before.entrySet()) reconcile(entry.getKey(), new State<>(true, entry.getValue()));
            for (K key : delegate.keySet()) if (!before.containsKey(key)) changed(key, true);
        }
    }

    @Override public int size() { return delegate.size(); }
    @Override public boolean containsKey(Object key) { return delegate.containsKey(key); }
    @Override public boolean containsValue(Object value) { return delegate.containsValue(value); }
    @Override public V get(Object key) { return delegate.get(key); }
    @Override public V getOrDefault(Object key, V fallback) { return delegate.getOrDefault(key, fallback); }
    @Override public V put(K key, V value) { return single(key, () -> delegate.put(key, value)); }
    @SuppressWarnings("unchecked")
    @Override public V remove(Object key) { return single((K) key, () -> delegate.remove(key)); }
    @SuppressWarnings("unchecked")
    @Override public boolean remove(Object key, Object value) { return single((K) key, () -> delegate.remove(key, value)); }
    @Override public V putIfAbsent(K key, V value) { return single(key, () -> delegate.putIfAbsent(key, value)); }
    @Override public V replace(K key, V value) { return single(key, () -> delegate.replace(key, value)); }
    @Override public boolean replace(K key, V oldValue, V newValue) {
        return single(key, () -> delegate.replace(key, oldValue, newValue));
    }
    @Override public V computeIfAbsent(K key, Function<? super K, ? extends V> function) {
        Objects.requireNonNull(function);
        beforePossibleResize();
        return single(key, () -> delegate.computeIfAbsent(key, function));
    }
    @Override public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> function) {
        return single(key, () -> delegate.computeIfPresent(key, function));
    }
    @Override public V compute(K key, BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);
        beforePossibleResize();
        return single(key, () -> delegate.compute(key, function));
    }
    @Override public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> function) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(function);
        beforePossibleResize();
        return single(key, () -> delegate.merge(key, value, function));
    }
    @Override public void putAll(Map<? extends K, ? extends V> map) {
        // HashMap may resize before asking the source iterator for its first entry.
        // Notify before even source.size(), including a joined foreign writer.
        beforePossibleResize();
        bulk(() -> delegate.putAll(map));
    }
    @Override public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        bulk(() -> delegate.replaceAll(function));
    }
    @Override public void forEach(BiConsumer<? super K, ? super V> action) { delegate.forEach(action); }
    @Override public void clear() { bulk(delegate::clear); }

    private <E> Iterator<E> iterator(Iterator<E> nativeIterator, Function<E, K> key) {
        return new Iterator<>() {
            private K current;
            private boolean selected;
            @Override public boolean hasNext() { return nativeIterator.hasNext(); }
            @Override public E next() {
                E result = nativeIterator.next();
                current = key.apply(result);
                selected = true;
                return result;
            }
            @Override public void remove() {
                // Native iterator owns its exact fail-fast checks and movable=false removal.
                nativeIterator.remove();
                if (selected) { changed(current, true); selected = false; }
            }
        };
    }

    @Override public Set<K> keySet() {
        return new AbstractSet<>() {
            @Override public int size() { return delegate.size(); }
            @Override public boolean contains(Object key) { return delegate.containsKey(key); }
            @Override public Iterator<K> iterator() { return TrackedRecipeMap.this.iterator(delegate.keySet().iterator(), Function.identity()); }
            @Override public void forEach(Consumer<? super K> action) { delegate.keySet().forEach(action); }
            @Override public Spliterator<K> spliterator() { return delegate.keySet().spliterator(); }
            @Override public boolean remove(Object key) {
                boolean present = delegate.containsKey(key);
                if (present) TrackedRecipeMap.this.remove(key);
                return present;
            }
            @Override public void clear() { TrackedRecipeMap.this.clear(); }
        };
    }

    @Override public Collection<V> values() {
        return new AbstractCollection<>() {
            @Override public int size() { return delegate.size(); }
            @Override public boolean contains(Object value) { return delegate.containsValue(value); }
            @Override public void clear() { TrackedRecipeMap.this.clear(); }
            @Override public void forEach(Consumer<? super V> action) { delegate.values().forEach(action); }
            @Override public Spliterator<V> spliterator() { return delegate.values().spliterator(); }
            @Override public Iterator<V> iterator() {
                Iterator<Entry<K, V>> entries = trackedEntries();
                return new Iterator<>() {
                    @Override public boolean hasNext() { return entries.hasNext(); }
                    @Override public V next() { return entries.next().getValue(); }
                    @Override public void remove() { entries.remove(); }
                };
            }
        };
    }

    private Iterator<Entry<K, V>> trackedEntries() {
        Iterator<Entry<K, V>> nativeIterator = iterator(delegate.entrySet().iterator(), Entry::getKey);
        return new Iterator<>() {
            @Override public boolean hasNext() { return nativeIterator.hasNext(); }
            @Override public void remove() { nativeIterator.remove(); }
            @Override public Entry<K, V> next() {
                return wrapEntry(nativeIterator.next());
            }
        };
    }

    private Entry<K, V> wrapEntry(Entry<K, V> entry) {
        return new Entry<>() {
                    @Override public K getKey() { return entry.getKey(); }
                    @Override public V getValue() { return entry.getValue(); }
                    @Override public V setValue(V value) {
                        V previous = entry.setValue(value);
                        // setValue on an entry detached by a prior removal must not reinsert it.
                        if (delegate.get(entry.getKey()) == value && delegate.containsKey(entry.getKey())) changed(entry.getKey(), false);
                        return previous;
                    }
                    @Override public boolean equals(Object other) { return entry.equals(other); }
                    @Override public int hashCode() { return entry.hashCode(); }
                    @Override public String toString() { return entry.toString(); }
        };
    }

    private Spliterator<Entry<K, V>> entrySpliterator(Spliterator<Entry<K, V>> nativeSpliterator) {
        return new Spliterator<>() {
            @Override public boolean tryAdvance(Consumer<? super Entry<K, V>> action) {
                Objects.requireNonNull(action);
                return nativeSpliterator.tryAdvance(entry -> action.accept(wrapEntry(entry)));
            }
            @Override public void forEachRemaining(Consumer<? super Entry<K, V>> action) {
                Objects.requireNonNull(action);
                nativeSpliterator.forEachRemaining(entry -> action.accept(wrapEntry(entry)));
            }
            @Override public Spliterator<Entry<K, V>> trySplit() {
                var split = nativeSpliterator.trySplit();
                return split == null ? null : entrySpliterator(split);
            }
            @Override public long estimateSize() { return nativeSpliterator.estimateSize(); }
            @Override public int characteristics() { return nativeSpliterator.characteristics(); }
            @Override public Comparator<? super Entry<K, V>> getComparator() { return nativeSpliterator.getComparator(); }
        };
    }

    @Override public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return delegate.size(); }
            @Override public Iterator<Entry<K, V>> iterator() { return trackedEntries(); }
            @Override public void forEach(Consumer<? super Entry<K, V>> action) {
                Objects.requireNonNull(action);
                delegate.entrySet().forEach(entry -> action.accept(wrapEntry(entry)));
            }
            @Override public Spliterator<Entry<K, V>> spliterator() { return entrySpliterator(delegate.entrySet().spliterator()); }
            @Override public boolean contains(Object entry) { return delegate.entrySet().contains(entry); }
            @Override public boolean remove(Object entry) {
                boolean[] removed = {false};
                bulk(() -> removed[0] = delegate.entrySet().remove(entry));
                return removed[0];
            }
            @Override public void clear() { TrackedRecipeMap.this.clear(); }
        };
    }
}
