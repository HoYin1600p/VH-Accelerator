package dev.hoyin1600p.vhaccelerator.shape;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Launch-only sharing of identical voxel shapes.
 *
 * <p>Decoration mods build block shapes in their constructors, once per block
 * and wood variant, from freshly created boxes (MrCrayfish's Furniture alone
 * spent ~0.4 s combining them in a Remastered test pack launch). While mod loading
 * runs, equal {@code Shapes.box} bounds return one shared instance, and
 * {@code Shapes.joinUnoptimized} and {@code VoxelShape.optimize} results are
 * reused by input identity. Because boxes are shared, every variant's
 * identical sequence of joins hits the cache without hashing shape contents.
 * Voxel shapes are immutable, and vanilla already shares
 * {@code Shapes.block()} and {@code Shapes.empty()} globally.</p>
 *
 * <p>Where another mod replaces {@code Shapes.box} (Canary), the box sharing
 * is not woven. Join and optimize keys then replace each cuboid leaf by the
 * first instance with the same class and content ({@link #leaf}); every
 * other shape, including every join result, is still keyed by identity.</p>
 *
 * <p>The cache closes and is cleared when Forge mod loading completes; later
 * gameplay uses vanilla behaviour. Generic types keep this class free of
 * Minecraft types so it can be tested directly.</p>
 */
public final class LaunchShapeCache {
    static final int MAX_ENTRIES = 250_000;
    private static final ConcurrentHashMap<BoxKey, Object> BOXES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<LeafContent, Object> LEAVES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<JoinKey, Object> JOINS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<IdentityKey, Object> OPTIMIZED = new ConcurrentHashMap<>();
    private static final LongAdder BOX_HITS = new LongAdder();
    private static final LongAdder LEAF_HITS = new LongAdder();
    private static final LongAdder JOIN_HITS = new LongAdder();
    private static final LongAdder OPTIMIZE_HITS = new LongAdder();
    private static final LongAdder STORES = new LongAdder();
    private static volatile boolean open = true;
    private static volatile boolean boxHookRan;

    private LaunchShapeCache() {
    }

    /**
     * True once the {@code Shapes.box} hook has run: equal boxes are then already one instance and
     * cuboid leaves need no content matching.
     */
    public static boolean boxesShared() {
        return boxHookRan;
    }

    public static boolean isOpen() {
        return open;
    }

    public static Object box(double x1, double y1, double z1, double x2, double y2, double z2) {
        if (!boxHookRan) {
            boxHookRan = true;
        }
        Object shape = BOXES.get(new BoxKey(x1, y1, z1, x2, y2, z2));
        if (shape != null) {
            BOX_HITS.increment();
        }
        return shape;
    }

    /** Returns the shared instance for these bounds, storing {@code shape} if first. */
    public static Object storeBox(
            double x1, double y1, double z1, double x2, double y2, double z2,
            Object shape
    ) {
        if (!open || BOXES.size() >= MAX_ENTRIES) {
            return shape;
        }
        Object existing = BOXES.putIfAbsent(new BoxKey(x1, y1, z1, x2, y2, z2), shape);
        if (existing == null) {
            STORES.increment();
            return shape;
        }
        return existing;
    }

    /**
     * The first shape seen with this exact class and content, or {@code shape}
     * itself once the cache is closed or full. {@code content} holds every
     * value that decides how the shape behaves.
     */
    public static Object leaf(Class<?> type, long[] content, Object shape) {
        LeafContent key = new LeafContent(type, content);
        Object existing = LEAVES.get(key);
        if (existing != null) {
            if (existing != shape) {
                LEAF_HITS.increment();
            }
            return existing;
        }
        if (!open || LEAVES.size() >= MAX_ENTRIES) {
            return shape;
        }
        existing = LEAVES.putIfAbsent(key, shape);
        return existing == null ? shape : existing;
    }

    /**
     * {@code sameInstance} records whether the caller passed one object twice:
     * {@code Shapes.joinUnoptimized} returns early for that case, so it must
     * not share an entry with two equal but distinct leaves.
     */
    public static Object join(Object first, Object second, Object operation, boolean sameInstance) {
        Object shape = JOINS.get(new JoinKey(first, second, operation, sameInstance));
        if (shape != null) {
            JOIN_HITS.increment();
        }
        return shape;
    }

    public static void storeJoin(
            Object first, Object second, Object operation, boolean sameInstance, Object result
    ) {
        if (open && JOINS.size() < MAX_ENTRIES
                && JOINS.putIfAbsent(new JoinKey(first, second, operation, sameInstance), result) == null) {
            STORES.increment();
        }
    }

    public static Object join(Object first, Object second, Object operation) {
        return join(first, second, operation, first == second);
    }

    public static void storeJoin(Object first, Object second, Object operation, Object result) {
        storeJoin(first, second, operation, first == second, result);
    }

    public static Object optimized(Object shape) {
        Object result = OPTIMIZED.get(new IdentityKey(shape));
        if (result != null) {
            OPTIMIZE_HITS.increment();
        }
        return result;
    }

    public static void storeOptimized(Object shape, Object result) {
        if (open && OPTIMIZED.size() < MAX_ENTRIES
                && OPTIMIZED.putIfAbsent(new IdentityKey(shape), result) == null) {
            STORES.increment();
        }
    }

    /** Closes the launch window and releases every cached shape. */
    public static void close(Consumer<String> statistics) {
        if (!open) {
            return;
        }
        open = false;
        long entries = BOXES.size() + LEAVES.size() + JOINS.size() + OPTIMIZED.size();
        BOXES.clear();
        LEAVES.clear();
        JOINS.clear();
        OPTIMIZED.clear();
        if (statistics != null) {
            statistics.accept("Launch voxel-shape cache closed: reused " + BOX_HITS.sum()
                    + " boxes, " + JOIN_HITS.sum() + " joins and " + OPTIMIZE_HITS.sum()
                    + " optimized shapes"
                    + (LEAF_HITS.sum() > 0 ? " (matched " + LEAF_HITS.sum() + " equal cuboid leaves)" : "")
                    + "; released " + entries + " entries");
        }
    }

    static void resetForTest() {
        open = true;
        boxHookRan = false;
        BOXES.clear();
        LEAVES.clear();
        JOINS.clear();
        OPTIMIZED.clear();
        BOX_HITS.reset();
        LEAF_HITS.reset();
        JOIN_HITS.reset();
        OPTIMIZE_HITS.reset();
        STORES.reset();
    }

    private record BoxKey(double x1, double y1, double z1, double x2, double y2, double z2) {
    }

    /** Exact class plus the raw bits of every value that defines the cuboid. */
    private static final class LeafContent {
        private final Class<?> type;
        private final long[] content;
        private final int hash;

        private LeafContent(Class<?> type, long[] content) {
            this.type = type;
            this.content = content;
            this.hash = 31 * type.hashCode() + Arrays.hashCode(content);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof LeafContent key
                    && key.type == type
                    && Arrays.equals(key.content, content);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /** Compares inputs by identity; operations are compared by identity too. */
    private static final class JoinKey {
        private final Object first;
        private final Object second;
        private final Object operation;
        private final boolean sameInstance;
        private final int hash;

        private JoinKey(Object first, Object second, Object operation, boolean sameInstance) {
            this.first = first;
            this.second = second;
            this.operation = operation;
            this.sameInstance = sameInstance;
            this.hash = 31 * (31 * System.identityHashCode(first)
                    + System.identityHashCode(second))
                    + System.identityHashCode(operation)
                    + (sameInstance ? 1 : 0);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof JoinKey key
                    && key.first == first
                    && key.second == second
                    && key.operation == operation
                    && key.sameInstance == sameInstance;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class IdentityKey {
        private final Object value;

        private IdentityKey(Object value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IdentityKey key && key.value == value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }
    }
}
