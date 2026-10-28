package dev.hoyin1600p.vhaccelerator.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An ordered snapshot of a handler set, split into one bucket per registry key.
 *
 * <p>Forge calls every object-holder handler after every registry event, and
 * nearly all of them return at once because they belong to another registry.
 * This index remembers, for each handler, the one key it can act for, so an
 * event for one key visits only that key's bucket plus the handlers that could
 * act for any key. Visits always come out in the order of the snapshot, so a
 * visited subset runs in exactly the order the full pass would have used.</p>
 *
 * <p>The classifier answers one of three things per handler: a key (the
 * handler does nothing unless an event names that key), {@link #ALWAYS} (no
 * knowledge, visit on every event) or {@link #UNSETTLED} (the handler still has
 * one-time work to do on its first visit, so it is visited on every event until
 * a new snapshot sees it settled).</p>
 */
public final class HolderDispatchIndex<H> {
    /** Visit on every event; nothing is known about this handler. */
    public static final Object ALWAYS = new Object();
    /** Visit on every event until a later snapshot classifies it with a key. */
    public static final Object UNSETTLED = new Object();

    /** Maps a handler to a key, {@link #ALWAYS} or {@link #UNSETTLED}. */
    @FunctionalInterface
    public interface Classifier<H> {
        Object classify(H handler);
    }

    private final Object[] ordered;
    private final Map<Object, int[]> buckets;
    private final int[] dynamic;
    private final int unsettled;

    private HolderDispatchIndex(Object[] ordered, Map<Object, int[]> buckets, int[] dynamic, int unsettled) {
        this.ordered = ordered;
        this.buckets = buckets;
        this.dynamic = dynamic;
        this.unsettled = unsettled;
    }

    /** Snapshots {@code handlers} in iteration order. A classifier failure puts the handler in the dynamic list. */
    public static <H> HolderDispatchIndex<H> build(Iterable<? extends H> handlers, Classifier<? super H> classifier) {
        List<Object> list = new ArrayList<>();
        for (H handler : handlers) {
            list.add(handler);
        }
        int size = list.size();
        Object[] ordered = list.toArray();
        Map<Object, int[]> counts = new HashMap<>();
        int[] keyOf = new int[size];
        List<Object> keys = new ArrayList<>();
        int[] kinds = new int[size]; // 0 keyed, 1 always, 2 unsettled
        int dynamicCount = 0;
        int unsettled = 0;
        for (int i = 0; i < size; i++) {
            Object kind;
            try {
                @SuppressWarnings("unchecked")
                H handler = (H) ordered[i];
                kind = classifier.classify(handler);
            } catch (RuntimeException failure) {
                kind = ALWAYS;
            }
            if (kind == null || kind == ALWAYS) {
                kinds[i] = 1;
                dynamicCount++;
            } else if (kind == UNSETTLED) {
                kinds[i] = 2;
                dynamicCount++;
                unsettled++;
            } else {
                int[] slot = counts.get(kind);
                if (slot == null) {
                    slot = new int[] {keys.size(), 0};
                    counts.put(kind, slot);
                    keys.add(kind);
                }
                slot[1]++;
                keyOf[i] = slot[0];
            }
        }
        int[][] byKey = new int[keys.size()][];
        for (int k = 0; k < byKey.length; k++) {
            byKey[k] = new int[counts.get(keys.get(k))[1]];
        }
        int[] fill = new int[byKey.length];
        int[] dynamic = new int[dynamicCount];
        int d = 0;
        for (int i = 0; i < size; i++) {
            if (kinds[i] == 0) {
                byKey[keyOf[i]][fill[keyOf[i]]++] = i;
            } else {
                dynamic[d++] = i;
            }
        }
        Map<Object, int[]> buckets = new HashMap<>(Math.max(16, keys.size() * 2));
        for (int k = 0; k < byKey.length; k++) {
            buckets.put(keys.get(k), byKey[k]);
        }
        return new HolderDispatchIndex<>(ordered, buckets, dynamic, unsettled);
    }

    public int size() {
        return ordered.length;
    }

    /** Handlers that are visited on every event (always plus unsettled). */
    public int dynamicCount() {
        return dynamic.length;
    }

    /** Handlers that were still unsettled when this snapshot was taken. */
    public int unsettledCount() {
        return unsettled;
    }

    public int bucketCount() {
        return buckets.size();
    }

    /** Number of handlers {@link #visit} would pass to its body for {@code key}. */
    public int visitCount(Object key) {
        int[] bucket = buckets.get(key);
        return dynamic.length + (bucket == null ? 0 : bucket.length);
    }

    /**
     * Passes the handlers that can act for {@code key} to {@code body}, in snapshot order.
     * An exception from {@code body} propagates; callers that need the full pass's
     * collect-and-continue behavior catch inside the body, as Forge's loop does.
     */
    @SuppressWarnings("unchecked")
    public void visit(Object key, Consumer<? super H> body) {
        int[] bucket = buckets.get(key);
        int[] dyn = dynamic;
        if (bucket == null) {
            for (int index : dyn) {
                body.accept((H) ordered[index]);
            }
            return;
        }
        if (dyn.length == 0) {
            for (int index : bucket) {
                body.accept((H) ordered[index]);
            }
            return;
        }
        int a = 0;
        int b = 0;
        while (a < dyn.length && b < bucket.length) {
            if (dyn[a] < bucket[b]) {
                body.accept((H) ordered[dyn[a++]]);
            } else {
                body.accept((H) ordered[bucket[b++]]);
            }
        }
        while (a < dyn.length) {
            body.accept((H) ordered[dyn[a++]]);
        }
        while (b < bucket.length) {
            body.accept((H) ordered[bucket[b++]]);
        }
    }
}
