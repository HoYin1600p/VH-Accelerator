package dev.hoyin1600p.vhaccelerator.compat.buildinggadgets;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.HashCommon;
import java.lang.invoke.VarHandle;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.slf4j.Logger;

/** Replaces only a reviewed BG-owned immutable map's redundant private JDK lookup table. */
public final class CompactTemplateLookup {
    private static final String JDK_BACKED = "com.google.common.collect.JdkBackedImmutableMap";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean ACCESS_WARNING = new AtomicBoolean();
    private CompactTemplateLookup() { }

    /** Maps up to this size are cheap enough to compact on the calling thread; larger ones are deferred. */
    static final int SYNCHRONOUS_LIMIT = 2048;

    /** Caller owns policy, exact archive/Guava guards and pre-publication BG map ownership. */
    public static boolean install(ImmutableMap<BlockPos, ?> map) {
        return install(map, Field::trySetAccessible);
    }

    /**
     * Startup-path entry: small maps are compacted now; large ones are queued for one low-priority daemon thread,
     * which builds and verifies the index and then publishes it. The caller only does a constant-time check.
     * Returns true when compaction was done or queued (not a promise that it will succeed).
     */
    public static boolean installDeferred(ImmutableMap<BlockPos, ?> map, Runnable onInstalled) {
        if (map == null || map.isEmpty() || !map.getClass().getName().equals(JDK_BACKED)) return false;
        if (map.size() <= SYNCHRONOUS_LIMIT) {
            boolean done = install(map);
            if (done && onInstalled != null) onInstalled.run();
            return done;
        }
        return DeferredCompaction.enqueue(map, onInstalled);
    }

    /** Stops the background worker without waiting for pending work. */
    public static void shutdownDeferred() {
        DeferredCompaction.shutdown();
    }

    // An injected access decision permits denial tests without a global security manager.
    static boolean install(ImmutableMap<BlockPos, ?> map, Predicate<Field> access) {
        if (map == null || map.isEmpty() || !map.getClass().getName().equals(JDK_BACKED)) return false;
        try {
            Field delegateField = map.getClass().getDeclaredField("delegateMap");
            Field entriesField = map.getClass().getDeclaredField("entries");
            if (delegateField.getType() != Map.class || entriesField.getType() != ImmutableList.class
                    || !privateFinalTransient(delegateField) || !privateFinalTransient(entriesField)) return false;
            if (!access.test(delegateField) || !access.test(entriesField)) {
                warnOnce("private Guava lookup fields are inaccessible");
                return false;
            }
            Object current = delegateField.get(map);
            if (current instanceof Lookup<?>) return false;
            if (current == null || current.getClass() != HashMap.class) return false;
            @SuppressWarnings("unchecked")
            ImmutableList<Map.Entry<BlockPos, Object>> entries = (ImmutableList<Map.Entry<BlockPos, Object>>) entriesField.get(map);
            if (entries == null || entries.size() != map.size() || ((Map<?, ?>) current).size() != entries.size()) return false;
            for (Map.Entry<?, ?> entry : entries) {
                if (entry == null || entry.getKey() == null || entry.getKey().getClass() != BlockPos.class || entry.getValue() == null) return false;
                if (Thread.currentThread().isInterrupted() && DeferredCompaction.stopping()) return false;
            }
            Lookup<Object> candidate = new Lookup<>(entries);
            Map<?, ?> original = (Map<?, ?>) current;
            int checked = 0;
            for (Map.Entry<BlockPos, Object> entry : entries) {
                if (original.get(entry.getKey()) != entry.getValue() || candidate.get(entry.getKey()) != entry.getValue()) {
                    warnOnce("verification mismatch");
                    return false;
                }
                if ((++checked & 0xFFFF) == 0 && Thread.currentThread().isInterrupted() && DeferredCompaction.stopping()) return false;
            }
            if (candidate.get(null) != original.get(null)) return false;
            BlockPos absent = BlockPos.ZERO;
            while (original.containsKey(absent)) absent = new BlockPos(absent.getX() + 1, 0, 0);
            if (candidate.get(absent) != null || original.get(absent) != null) return false;
            return publish(map, delegateField, current, candidate);
        } catch (ReflectiveOperationException | SecurityException failure) {
            warnOnce(failure.getClass().getSimpleName());
            return false;
        } catch (RuntimeException | LinkageError unsupported) {
            warnOnce(unsupported.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Swaps the delegate. The candidate is complete and immutable (final fields, so safe publication holds even for a
     * racy read); fences order its construction before the store. Concurrent readers see the old or the new delegate,
     * never anything else. Field.set on a final instance field is a volatile store on JDK 17.
     */
    private static boolean publish(ImmutableMap<BlockPos, ?> map, Field delegateField, Object original,
                                   Lookup<Object> candidate) throws ReflectiveOperationException {
        synchronized (map) {
            if (delegateField.get(map) != original) return false;
            VarHandle.releaseFence();
            delegateField.set(map, candidate);
            VarHandle.fullFence();
            if (delegateField.get(map) != candidate) {
                delegateField.set(map, original);
                return false;
            }
            return true;
        }
    }

    /** One daemon thread at minimum priority; pending maps are weakly held so discarded templates are not retained. */
    static final class DeferredCompaction {
        private static final LinkedBlockingQueue<Task> QUEUE = new LinkedBlockingQueue<>();
        private static Thread worker;
        private static volatile boolean stopping;

        /** The subject (a map, or a template that owns one) is held weakly; the action must not capture it. */
        private record Task(WeakReference<Object> subject, Predicate<Object> action, Runnable onInstalled) { }

        static boolean stopping() { return stopping; }

        static boolean enqueue(ImmutableMap<BlockPos, ?> map, Runnable onInstalled) {
            return enqueue(map, subject -> install((ImmutableMap<BlockPos, ?>) subject), onInstalled);
        }

        static synchronized boolean enqueue(Object subject, Predicate<Object> action, Runnable onInstalled) {
            if (stopping) return false;
            QUEUE.add(new Task(new WeakReference<>(subject), action, onInstalled));
            if (worker == null || !worker.isAlive()) {
                worker = new Thread(DeferredCompaction::run, "VHA BuildingGadgets template compaction");
                worker.setDaemon(true);
                worker.setPriority(Thread.MIN_PRIORITY);
                worker.start();
            }
            return true;
        }

        private static void run() {
            try {
                while (!stopping) {
                    Task task = QUEUE.take();
                    if (stopping) return;
                    Object subject = task.subject.get();
                    if (subject == null) continue;
                    try {
                        if (task.action.test(subject) && task.onInstalled != null) task.onInstalled.run();
                    } catch (RuntimeException | LinkageError failure) {
                        warnOnce(failure.getClass().getSimpleName());
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        static synchronized void shutdown() {
            stopping = true;
            QUEUE.clear();
            if (worker != null) worker.interrupt();
            worker = null;
        }

        /** Test hook: resume after shutdown. */
        static synchronized void reset() {
            QUEUE.clear();
            stopping = false;
        }

        static int pending() { return QUEUE.size(); }
    }

    private static boolean privateFinalTransient(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isPrivate(modifiers) && Modifier.isFinal(modifiers) && Modifier.isTransient(modifiers)
                && !Modifier.isStatic(modifiers);
    }

    private static void warnOnce(String reason) {
        if (ACCESS_WARNING.compareAndSet(false, true)) {
            LOGGER.warn("Building Gadgets compact lookup unavailable; retaining original map ({})", reason);
        }
    }

    private static final class Lookup<V> extends AbstractMap<BlockPos, V> {
        private final ImmutableList<Map.Entry<BlockPos, V>> entries;
        private final int[] slots;
        private final Set<Map.Entry<BlockPos, V>> entrySet;
        private volatile SoftReference<HashMap<BlockPos, V>> fallbackReference;

        Lookup(ImmutableList<Map.Entry<BlockPos, V>> entries) {
            this.entries = entries;
            slots = new int[HashCommon.arraySize(entries.size(), 0.75f)];
            for (int index = 0; index < entries.size(); index++) {
                BlockPos key = entries.get(index).getKey();
                int slot = slot(key.getX(), key.getY(), key.getZ());
                while (slots[slot] != 0) {
                    BlockPos previous = entries.get(slots[slot] - 1).getKey();
                    if (sameCoordinates(key, previous)) throw new IllegalArgumentException("Repeated coordinates");
                    slot = slot + 1 & slots.length - 1;
                }
                slots[slot] = index + 1;
            }
            entrySet = Collections.unmodifiableSet(new AbstractSet<>() {
                @Override public int size() { return entries.size(); }
                @Override public Iterator<Map.Entry<BlockPos, V>> iterator() { return entries.iterator(); }
            });
        }

        private int slot(int x, int y, int z) {
            long horizontal = (long) x << 32 ^ Integer.toUnsignedLong(z);
            long hash = HashCommon.mix(horizontal) ^ Long.rotateLeft(HashCommon.mix((long) y), 21);
            return (int) HashCommon.mix(hash) & slots.length - 1;
        }

        private static boolean sameCoordinates(Vec3i left, Vec3i right) {
            return left.getX() == right.getX() && left.getY() == right.getY() && left.getZ() == right.getZ();
        }

        @Override public V get(Object query) {
            if (query == null) return null;
            Class<?> queryClass = query.getClass();
            if (queryClass == BlockPos.class || queryClass == Vec3i.class || queryClass == BlockPos.MutableBlockPos.class) {
                Vec3i key = (Vec3i) query;
                int queryHash = key.hashCode();
                int slot = slot(key.getX(), key.getY(), key.getZ());
                while (slots[slot] != 0) {
                    Map.Entry<BlockPos, V> entry = entries.get(slots[slot] - 1);
                    if (sameCoordinates(key, entry.getKey())) {
                        BlockPos stored = entry.getKey();
                        if (key == stored || queryHash == stored.hashCode() && key.equals(stored)) return entry.getValue();
                        return null;
                    }
                    slot = slot + 1 & slots.length - 1;
                }
                return null;
            }
            // Preserve arbitrary query hash/equals and JDK tree-bin traversal semantics: ask a real HashMap.
            // It is built once on first unusual query and held softly, so it is reclaimed under memory pressure
            // and costs nothing for the standard query classes. Stored keys are exact immutable BlockPos;
            // building adds no user callbacks, and the query is hashed exactly once, by HashMap itself.
            return fallback().get(query);
        }

        private HashMap<BlockPos, V> fallback() {
            SoftReference<HashMap<BlockPos, V>> reference = fallbackReference;
            HashMap<BlockPos, V> cached = reference == null ? null : reference.get();
            if (cached != null) return cached;
            synchronized (this) {
                reference = fallbackReference;
                cached = reference == null ? null : reference.get();
                if (cached == null) {
                    cached = Maps.newHashMapWithExpectedSize(entries.size());
                    for (Map.Entry<BlockPos, V> entry : entries) cached.putIfAbsent(entry.getKey(), entry.getValue());
                    fallbackReference = new SoftReference<>(cached);
                }
                return cached;
            }
        }

        @Override public int size() { return entries.size(); }
        @Override public Set<Map.Entry<BlockPos, V>> entrySet() { return entrySet; }
        @Override public Set<BlockPos> keySet() { return Collections.unmodifiableSet(super.keySet()); }
        @Override public Collection<V> values() { return Collections.unmodifiableCollection(super.values()); }
        @Override public V put(BlockPos key, V value) { throw immutable(); }
        @Override public V remove(Object key) { throw immutable(); }
        @Override public void putAll(Map<? extends BlockPos, ? extends V> values) { throw immutable(); }
        @Override public void clear() { throw immutable(); }
        @Override public V putIfAbsent(BlockPos key, V value) { throw immutable(); }
        @Override public boolean remove(Object key, Object value) { throw immutable(); }
        @Override public boolean replace(BlockPos key, V old, V value) { throw immutable(); }
        @Override public V replace(BlockPos key, V value) { throw immutable(); }
        @Override public void replaceAll(BiFunction<? super BlockPos, ? super V, ? extends V> function) { throw immutable(); }
        @Override public V computeIfAbsent(BlockPos key, Function<? super BlockPos, ? extends V> function) { throw immutable(); }
        @Override public V computeIfPresent(BlockPos key, BiFunction<? super BlockPos, ? super V, ? extends V> function) { throw immutable(); }
        @Override public V compute(BlockPos key, BiFunction<? super BlockPos, ? super V, ? extends V> function) { throw immutable(); }
        @Override public V merge(BlockPos key, V value, BiFunction<? super V, ? super V, ? extends V> function) { throw immutable(); }
        private UnsupportedOperationException immutable() { return new UnsupportedOperationException("immutable lookup"); }
    }
}
