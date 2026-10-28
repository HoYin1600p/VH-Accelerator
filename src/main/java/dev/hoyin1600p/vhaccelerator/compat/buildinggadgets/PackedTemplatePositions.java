package dev.hoyin1600p.vhaccelerator.compat.buildinggadgets;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

/**
 * Holds the entries of one Building Gadgets template as packed arrays (one {@code long} per position plus a
 * palette index) instead of one immutable-map entry and one {@link BlockPos} per block.
 *
 * <p>The template keeps a one-entry <em>marker map</em> in its private {@code map} field; the single value is
 * this object. The methods of {@code Template} only ever iterate that map or hand it to a build view, so two
 * redirects serve them without rebuilding anything: {@link #entries} replaces the {@code entrySet().iterator()}
 * call and streams the entries straight from the arrays, and {@link #createView} gives the build view a light
 * read-only map over the arrays. Entries come in the original order with the original value objects.
 * {@link #resolve} rebuilds a full immutable map (kept weakly) for diagnostics and tests.
 */
public final class PackedTemplatePositions {
    /** Maps smaller than this keep their original form: the packed form would not save anything. */
    static final int MINIMUM_SIZE = 8;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    /** Only ever compared by identity and used as the marker map's single key. */
    private static final BlockPos MARKER_KEY = new BlockPos(Integer.MIN_VALUE >> 6, -2048, Integer.MAX_VALUE >> 6);
    private static final Class<?> MARKER_CLASS = ImmutableMap.of(MARKER_KEY, new Object()).getClass();

    private final long[] positions;
    private final Object[] palette;
    // Exactly one of these is set, by palette size: up to 256, up to 65536, or larger.
    private final byte[] byteIds;
    private final char[] charIds;
    private final int[] intIds;
    private volatile WeakReference<ImmutableMap<BlockPos, Object>> materialized;

    private PackedTemplatePositions(long[] positions, Object[] palette, int[] ids) {
        this.positions = positions;
        this.palette = palette;
        if (palette.length <= 1 << 8) {
            byteIds = new byte[ids.length];
            for (int i = 0; i < ids.length; i++) byteIds[i] = (byte) ids[i];
            charIds = null;
            intIds = null;
        } else if (palette.length <= 1 << 16) {
            charIds = new char[ids.length];
            for (int i = 0; i < ids.length; i++) charIds[i] = (char) ids[i];
            byteIds = null;
            intIds = null;
        } else {
            intIds = ids;
            byteIds = null;
            charIds = null;
        }
    }

    public int size() { return positions.length; }

    private int id(int index) {
        if (byteIds != null) return byteIds[index] & 0xFF;
        if (charIds != null) return charIds[index];
        return intIds[index];
    }

    /** Position and value of one entry, in original entry order. */
    BlockPos position(int index) { return BlockPos.of(positions[index]); }
    Object value(int index) { return palette[id(index)]; }

    /** True for the marker map a successful {@link #pack} returns. */
    public static boolean isMarker(ImmutableMap<?, ?> map) {
        return map != null && map.getClass() == MARKER_CLASS && map.get(MARKER_KEY) instanceof PackedTemplatePositions;
    }

    /**
     * What every redirected read of a template's {@code map} field returns: the field's own map, or for a packed
     * template the map rebuilt from the packed arrays. Cheap for unpacked templates: one class comparison.
     */
    public static ImmutableMap<BlockPos, ?> resolve(ImmutableMap<BlockPos, ?> raw) {
        if (raw == null || raw.getClass() != MARKER_CLASS) return raw;
        return raw.get(MARKER_KEY) instanceof PackedTemplatePositions packed ? packed.materialize() : raw;
    }

    private static PackedTemplatePositions of(ImmutableSet<?> entrySet) {
        if (entrySet == null || entrySet.size() != 1) return null;
        Object first = entrySet.iterator().next();
        return first instanceof Map.Entry<?, ?> entry && entry.getKey() == MARKER_KEY
                && entry.getValue() instanceof PackedTemplatePositions packed ? packed : null;
    }

    /**
     * Replaces {@code map.entrySet().iterator()} in the template methods that only iterate: a packed template's
     * entries are produced from the arrays in index order, anything else iterates as before.
     */
    public static UnmodifiableIterator<? extends Map.Entry<?, ?>> entries(ImmutableSet<? extends Map.Entry<?, ?>> entrySet) {
        PackedTemplatePositions packed = of(entrySet);
        return packed == null ? entrySet.iterator() : packed.streamingIterator();
    }

    /** One entry object, refilled by each {@code next()}; the key is a fresh position every time. */
    private final class Cursor implements Map.Entry<BlockPos, Object> {
        private BlockPos key;
        private Object value;
        @Override public BlockPos getKey() { return key; }
        @Override public Object getValue() { return value; }
        @Override public Object setValue(Object replacement) { throw new UnsupportedOperationException(); }
        @Override public boolean equals(Object other) {
            return other instanceof Map.Entry<?, ?> entry && java.util.Objects.equals(key, entry.getKey())
                    && java.util.Objects.equals(value, entry.getValue());
        }
        @Override public int hashCode() { return key.hashCode() ^ value.hashCode(); }
        @Override public String toString() { return key + "=" + value; }
    }

    /**
     * For the template methods that read each entry (key and value) right away and keep neither the entry nor the
     * iterator: the same {@link Map.Entry} object is refilled for every element, which saves one allocation per block.
     * Anything that may retain entries gets {@link #entryIterator()} instead.
     */
    private UnmodifiableIterator<Map.Entry<BlockPos, Object>> streamingIterator() {
        Cursor cursor = new Cursor();
        return new UnmodifiableIterator<>() {
            private int next;
            @Override public boolean hasNext() { return next < positions.length; }
            @Override public Map.Entry<BlockPos, Object> next() {
                if (next >= positions.length) throw new NoSuchElementException();
                int index = next++;
                cursor.key = BlockPos.of(positions[index]);
                cursor.value = palette[id(index)];
                return cursor;
            }
        };
    }

    UnmodifiableIterator<Map.Entry<BlockPos, Object>> entryIterator() {
        return new UnmodifiableIterator<>() {
            private int next;
            @Override public boolean hasNext() { return next < positions.length; }
            @Override public Map.Entry<BlockPos, Object> next() {
                if (next >= positions.length) throw new NoSuchElementException();
                int index = next++;
                return Maps.immutableEntry(BlockPos.of(positions[index]), palette[id(index)]);
            }
        };
    }

    /** A read-only map over the arrays (entries in the original order, fresh positions on each read). */
    Map<BlockPos, Object> view() {
        return new AbstractMap<>() {
            private final Set<Map.Entry<BlockPos, Object>> entries = new AbstractSet<>() {
                @Override public Iterator<Map.Entry<BlockPos, Object>> iterator() { return entryIterator(); }
                @Override public int size() { return positions.length; }
            };
            @Override public Set<Map.Entry<BlockPos, Object>> entrySet() { return entries; }
            @Override public int size() { return positions.length; }
        };
    }

    private static volatile Method createUnsafe;

    /**
     * Replaces the {@code PositionalBuildView.createUnsafe(context, map, region)} call of
     * {@code createViewInContext}: a packed template's map is passed as {@link #view()}, so a paste does not rebuild it.
     */
    public static Object createView(Object context, Map<?, ?> map, Object region) {
        Map<?, ?> source = map;
        if (map instanceof ImmutableMap<?, ?> immutable && immutable.getClass() == MARKER_CLASS
                && immutable.get(MARKER_KEY) instanceof PackedTemplatePositions packed) source = packed.view();
        try {
            Method method = createUnsafe;
            if (method == null) {
                Class<?> type = Class.forName("com.direwolf20.buildinggadgets.common.tainted.building.view.PositionalBuildView",
                        false, context.getClass().getClassLoader());
                for (Method candidate : type.getMethods()) {
                    if (candidate.getName().equals("createUnsafe") && candidate.getParameterCount() == 3) method = candidate;
                }
                if (method == null) throw new NoSuchMethodException("createUnsafe");
                createUnsafe = method;
            }
            return method.invoke(null, context, source, region);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Building Gadgets build view factory unavailable", failure);
        }
    }

    /** A full immutable map as the original code built it (diagnostics and tests; production reads stream instead). */
    ImmutableMap<BlockPos, Object> materialize() {
        WeakReference<ImmutableMap<BlockPos, Object>> reference = materialized;
        ImmutableMap<BlockPos, Object> cached = reference == null ? null : reference.get();
        if (cached != null) return cached;
        ImmutableMap.Builder<BlockPos, Object> builder = ImmutableMap.builderWithExpectedSize(positions.length);
        for (int i = 0; i < positions.length; i++) builder.put(BlockPos.of(positions[i]), palette[id(i)]);
        ImmutableMap<BlockPos, Object> built = builder.build();
        materialized = new WeakReference<>(built);
        return built;
    }

    /**
     * Returns the marker map for {@code source}, or null when it is not eligible. Every coordinate must survive
     * the packing exactly, keys must be plain {@link BlockPos}, and the packed arrays are compared with the source
     * entry by entry before anything is returned.
     */
    public static ImmutableMap<BlockPos, ?> pack(ImmutableMap<BlockPos, ?> source) {
        if (source == null || source.size() < MINIMUM_SIZE || source.getClass() == MARKER_CLASS) return null;
        int size = source.size();
        long[] positions = new long[size];
        int[] ids = new int[size];
        IdentityHashMap<Object, Integer> index = new IdentityHashMap<>();
        ArrayList<Object> values = new ArrayList<>();
        int count = 0;
        for (Map.Entry<BlockPos, ?> entry : source.entrySet()) {
            if (count == size) return null;
            BlockPos key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || key.getClass() != BlockPos.class || value == null) return null;
            long packed = key.asLong();
            if (BlockPos.getX(packed) != key.getX() || BlockPos.getY(packed) != key.getY()
                    || BlockPos.getZ(packed) != key.getZ()) return null;
            Integer id = index.get(value);
            if (id == null) {
                id = values.size();
                index.put(value, id);
                values.add(value);
            }
            positions[count] = packed;
            ids[count++] = id;
            if ((count & 0xFFFF) == 0 && Thread.currentThread().isInterrupted() && CompactTemplateLookup.DeferredCompaction.stopping()) return null;
        }
        if (count != size) return null;
        PackedTemplatePositions result = new PackedTemplatePositions(positions, values.toArray(), ids);
        // Verification against the live source, not against the arrays that were just filled.
        int checked = 0;
        for (Map.Entry<BlockPos, ?> entry : source.entrySet()) {
            if (checked == size) return null;
            BlockPos key = entry.getKey();
            BlockPos restored = result.position(checked);
            if (restored.getX() != key.getX() || restored.getY() != key.getY() || restored.getZ() != key.getZ()
                    || result.value(checked) != entry.getValue()) {
                warnOnce("verification mismatch");
                return null;
            }
            checked++;
        }
        if (checked != size) return null;
        return ImmutableMap.of(MARKER_KEY, result);
    }

    /**
     * Packs {@code template}'s map and publishes the marker. Falls back to the older lookup-only compaction when
     * the map is not eligible. Small maps are done on the calling thread, large ones on the shared low-priority
     * worker, which only holds the template weakly.
     */
    public static boolean installDeferred(Object template, Field mapField, ImmutableMap<BlockPos, ?> map, Runnable onInstalled) {
        if (template == null || map == null || map.isEmpty() || isMarker(map)) return false;
        if (map.size() <= CompactTemplateLookup.SYNCHRONOUS_LIMIT) {
            boolean done = installNow(template, mapField);
            if (done && onInstalled != null) onInstalled.run();
            return done;
        }
        return CompactTemplateLookup.DeferredCompaction.enqueue(template, subject -> installNow(subject, mapField), onInstalled);
    }

    static boolean installNow(Object template, Field mapField) {
        try {
            @SuppressWarnings("unchecked")
            ImmutableMap<BlockPos, ?> expected = (ImmutableMap<BlockPos, ?>) mapField.get(template);
            if (expected == null || isMarker(expected)) return false;
            ImmutableMap<BlockPos, ?> marker = pack(expected);
            if (marker == null) return CompactTemplateLookup.install(expected);
            synchronized (template) {
                if (mapField.get(template) != expected) return false;
                mapField.set(template, marker);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warnOnce(failure.getClass().getSimpleName());
            return false;
        }
    }

    private static void warnOnce(String reason) {
        if (WARNED.compareAndSet(false, true)) {
            LOGGER.warn("Building Gadgets packed template positions unavailable for a template; retaining its original map ({})", reason);
        }
    }
}
