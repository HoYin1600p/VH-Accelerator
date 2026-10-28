package dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** A generation-owned item index over mutation-observed, fresh CT maps. */
public final class GenerationOutputIndex {
    private final Thread owner = Thread.currentThread();
    private final IdentityHashMap<TrackedRecipeMap<?, ?>, MapIndex<?, ?>> maps = new IdentityHashMap<>();
    private boolean failed;
    private boolean closed;
    private long visits;
    private long classifications;
    private long orderRefreshes;

    @SuppressWarnings("unchecked")
    public <K, V> boolean tryRemove(Map<K, V> backing, Map<K, ?> byName, Predicate<V> original,
            Supplier<Object> requestedKey, Function<V, Object> stableOutputKey) {
        if (!usable() || !OutputRemovalContext.indexAllowed() || requestedKey.get() == null
                || !(backing instanceof TrackedRecipeMap<?, ?> observed) || !observed.eligible() || observed.busy()) return false;
        TrackedRecipeMap<K, V> tracked = (TrackedRecipeMap<K, V>) observed;
        MapIndex<K, V> index = (MapIndex<K, V>) maps.get(tracked);
        if (index == null) {
            index = new MapIndex<>(tracked, stableOutputKey);
            maps.put(tracked, index);
        }
        index.prepare();
        if (!index.plainBins) return false;
        long cursor = -1;
        boolean conservativeRemainder = false;
        while (true) {
            index.prepare();
            Object requested = requestedKey.get();
            if (requested == null) conservativeRemainder = true;
            Entry<K, V> entry = conservativeRemainder ? index.next(index.order, cursor) : index.nextCandidate(requested, cursor);
            if (entry == null) return true;
            cursor = entry.rank;
            boolean hadNext = index.order.higherKey(cursor) != null;
            var nativeToken = tracked.nativeToken();
            long structure = tracked.structure();
            CallbackFrame<K> frame = index.enter(entry.id);
            try {
            visits++;
            boolean remove = original.test(entry.recipe);
            if (remove) {
                // CT removes byName before its iterator performs its fail-fast check.
                byName.remove(entry.id);
                checkToken(nativeToken);
                if (tracked.structure() != structure) throw new java.util.ConcurrentModificationException();
                // For ordinary HashMap nodes, movable=true and iterator's movable=false are identical.
                if (frame.cursor == null) tracked.remove(entry.id);
                else { frame.cursor.remove(); tracked.nativeIteratorRemoved(entry.id); }
            } else if (hadNext) {
                checkToken(nativeToken);
                if (tracked.structure() != structure) throw new java.util.ConcurrentModificationException();
            } else {
                // The stock iterator already had next=null; an unsuccessful final predicate never checks again.
                return true;
            }
            if (frame.cursor != null) {
                index.nativeRemainder(frame.cursor, byName, original);
                return true;
            }
            if (frame.disowned || !usable() || maps.get(tracked) != index || !tracked.eligible() || requestedKey.get() == null) {
                // Ownership may be lost inside an unknown callback. Resume with a real native
                // iterator, skipping only already-visited positions and never replaying callbacks.
                index.originalRemainder(cursor, byName, original);
                return true;
            }
            } finally { index.exit(frame); }
        }
    }

    private static void checkToken(java.util.Iterator<?> token) {
        try { token.next(); }
        catch (NoSuchElementException unchangedEmptyMap) { /* nextNode checks modCount before exhaustion. */ }
    }

    /** Explicit invalidation is for ownership changes, never an unrelated script action. */
    public void invalidate() {
        if (Thread.currentThread() == owner) {
            maps.values().forEach(MapIndex::detach);
            maps.clear();
        }
    }
    public void fail() { if (Thread.currentThread() == owner) { failed = true; invalidate(); } }
    public void close() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Generation closed on another thread");
        invalidate();
        closed = true;
    }
    public boolean usable() { return Thread.currentThread() == owner && !failed && !closed; }
    public long visits() { return visits; }
    public long classifications() { return classifications; }
    public long orderRefreshes() { return orderRefreshes; }

    private static final class Entry<K, V> {
        final K id;
        final V recipe;
        final Object item;
        final long rank;
        Entry(K id, V recipe, Object item, long rank) { this.id = id; this.recipe = recipe; this.item = item; this.rank = rank; }
    }

    private static final class CallbackFrame<K> {
        final K id;
        volatile java.util.Iterator<K> cursor;
        volatile boolean disowned;
        CallbackFrame(K id) { this.id = id; }
    }

    private final class MapIndex<K, V> {
        final TrackedRecipeMap<K, V> map;
        final Function<V, Object> classifier;
        final Map<K, Entry<K, V>> entries = new HashMap<>();
        final IdentityHashMap<Object, NavigableMap<Long, Entry<K, V>>> items = new IdentityHashMap<>();
        final NavigableMap<Long, Entry<K, V>> unknown = new TreeMap<>();
        final NavigableMap<Long, Entry<K, V>> order = new TreeMap<>();
        final Set<K> dirty = new HashSet<>();
        final BiConsumer<K, Boolean> observer;
        final Runnable orderObserver;
        final java.util.List<CallbackFrame<K>> active = new java.util.ArrayList<>();
        boolean detached;
        boolean orderDirty = true;
        boolean plainBins;

        MapIndex(TrackedRecipeMap<K, V> map, Function<V, Object> classifier) {
            this.map = map;
            this.classifier = classifier;
            observer = (key, structural) -> {
                dirty.add(key);
                // An add followed by a remove can still have resized the backing table.
                if (structural && map.containsKey(key)) orderDirty = true;
            };
            orderObserver = () -> {
                synchronized (active) {
                    orderDirty = true;
                    for (CallbackFrame<K> frame : active) {
                        if (frame.cursor != null || frame.disowned) continue;
                        try {
                            var cursor = map.nativeToken();
                            boolean found = false;
                            while (cursor.hasNext()) {
                                if (java.util.Objects.equals(cursor.next(), frame.id)) { found = true; break; }
                            }
                            if (found) frame.cursor = cursor;
                            else frame.disowned = true;
                        } catch (RuntimeException | LinkageError unavailable) {
                            // The caller's pre-callback token retains the original error timing.
                            frame.disowned = true;
                        }
                    }
                }
            };
            map.observe(observer);
            map.observeOrder(orderObserver);
        }
        CallbackFrame<K> enter(K id) {
            CallbackFrame<K> frame = new CallbackFrame<>(id);
            synchronized (active) { active.add(frame); }
            return frame;
        }
        void exit(CallbackFrame<K> frame) {
            synchronized (active) {
                active.remove(frame);
                if (detached && active.isEmpty()) map.unobserveOrder(orderObserver);
            }
        }
        void detach() {
            map.unobserve(observer);
            synchronized (active) {
                detached = true;
                if (active.isEmpty()) map.unobserveOrder(orderObserver);
            }
        }
        Object classify(V value) { classifications++; return classifier.apply(value); }
        NavigableMap<Long, Entry<K, V>> bucket(Object item) {
            return item == null ? unknown : items.computeIfAbsent(item, ignored -> new TreeMap<>());
        }
        void unlink(Entry<K, V> entry) {
            order.remove(entry.rank);
            bucket(entry.item).remove(entry.rank);
            entries.remove(entry.id);
        }
        void link(Entry<K, V> entry) {
            entries.put(entry.id, entry);
            order.put(entry.rank, entry);
            bucket(entry.item).put(entry.rank, entry);
        }
        void prepare() {
            if (!orderDirty) {
                for (K key : dirty) if (map.containsKey(key) && !entries.containsKey(key)) { orderDirty = true; break; }
            }
            if (orderDirty) {
                // Add batches may resize/repartition bins. Repair ranks, retaining every unchanged classification.
                Map<K, Entry<K, V>> before = new HashMap<>(entries);
                entries.clear(); items.clear(); unknown.clear(); order.clear();
                plainBins = true;
                long rank = 0;
                for (var nativeEntry : map.nativeEntries()) {
                    if (!nativeEntry.getClass().getName().equals("java.util.HashMap$Node")) plainBins = false;
                    Entry<K, V> old = before.get(nativeEntry.getKey());
                    Object item = old != null && old.recipe == nativeEntry.getValue()
                            ? old.item : classify(nativeEntry.getValue());
                    link(new Entry<>(nativeEntry.getKey(), nativeEntry.getValue(), item, rank++));
                }
                orderDirty = false;
                orderRefreshes++;
            } else {
                for (K key : dirty) {
                    Entry<K, V> old = entries.get(key);
                    if (old == null) continue;
                    boolean present = map.containsKey(key);
                    V recipe = map.get(key);
                    if (!present || old.recipe != recipe) {
                        unlink(old);
                        if (present) link(new Entry<>(key, recipe, classify(recipe), old.rank));
                    }
                }
            }
            dirty.clear();
        }
        Entry<K, V> next(NavigableMap<Long, Entry<K, V>> values, long cursor) {
            if (values == null) return null;
            var result = values.higherEntry(cursor);
            return result == null ? null : result.getValue();
        }
        Entry<K, V> nextCandidate(Object requested, long cursor) {
            Entry<K, V> best = next(unknown, cursor);
            if (requested instanceof RequestedKeys many) {
                // A tag request: the earliest entry among the buckets of every expanded item.
                for (Object key : many.keys()) {
                    Entry<K, V> candidate = next(items.get(key), cursor);
                    if (candidate != null && (best == null || candidate.rank < best.rank)) best = candidate;
                }
                return best;
            }
            Entry<K, V> a = next(items.get(requested), cursor);
            return a == null ? best : best == null || a.rank < best.rank ? a : best;
        }
        void originalRemainder(long cursor, Map<K, ?> byName, Predicate<V> original) {
            var iterator = map.keySet().iterator();
            while (iterator.hasNext()) {
                K key = iterator.next();
                Entry<K, V> before = entries.get(key);
                if (before != null && before.rank <= cursor) continue;
                visits++;
                if (original.test(map.get(key))) {
                    byName.remove(key);
                    iterator.remove();
                }
            }
        }
        void nativeRemainder(java.util.Iterator<K> iterator, Map<K, ?> byName, Predicate<V> original) {
            while (iterator.hasNext()) {
                K key = iterator.next();
                visits++;
                if (original.test(map.get(key))) {
                    byName.remove(key);
                    iterator.remove();
                    map.nativeIteratorRemoved(key);
                }
            }
        }
    }
}
