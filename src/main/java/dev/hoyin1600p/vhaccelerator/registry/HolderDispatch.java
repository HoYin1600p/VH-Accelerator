package dev.hoyin1600p.vhaccelerator.registry;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Keeps a {@link HolderDispatchIndex} of a mutable handler set up to date and
 * dispatches one event through it.
 *
 * <p>The index is a snapshot of the set's iteration order, so it is rebuilt
 * whenever the set may have changed: a caller reports every mutation with
 * {@link #mutated()}, and a size change is noticed on its own. It is also
 * rebuilt after a pass that visited unsettled handlers, because those become
 * keyed once they have run. After {@code maxRebuilds} rebuilds the dispatcher
 * switches itself off for good and every event takes the original full pass.</p>
 */
public final class HolderDispatch<H> {
    private final HolderDispatchIndex.Classifier<? super H> classifier;
    private final int maxRebuilds;
    private final AtomicInteger mutations = new AtomicInteger();
    private HolderDispatchIndex<H> index;
    private int builtMutations;
    private int builtSize;
    private boolean rebuildNext;
    private boolean disabled;
    private int builds;
    private long events;
    private long visited;
    private long fullPassEquivalent;

    public HolderDispatch(HolderDispatchIndex.Classifier<? super H> classifier, int maxRebuilds) {
        this.classifier = Objects.requireNonNull(classifier);
        this.maxRebuilds = maxRebuilds;
    }

    /** Reports that the handler set changed (add, remove or prune). Cheap and thread-safe. */
    public void mutated() {
        mutations.incrementAndGet();
    }

    public synchronized boolean disabled() {
        return disabled;
    }

    /**
     * Drops the index (about 4 MB in a large pack). The next dispatch rebuilds it from the live handler set,
     * exactly as after any mutation; counters are kept and the rebuild budget is not spent. A visit already
     * running keeps its own snapshot.
     */
    public synchronized void release() {
        index = null;
        rebuildNext = false;
    }

    /**
     * Visits the handlers of {@code source} that can act for {@code key}, in the set's iteration order.
     *
     * @param lock the monitor that guards every mutation of {@code source}
     * @return false when nothing was visited and the caller must run the original full pass
     */
    public boolean dispatch(Collection<H> source, Object lock, Object key, Consumer<? super H> body) {
        HolderDispatchIndex<H> snapshot = prepare(source, lock);
        if (snapshot == null) {
            return false;
        }
        // Nothing here catches: whatever the body throws propagates, and no handler is visited twice.
        snapshot.visit(key, body);
        record(snapshot, key);
        return true;
    }

    /**
     * Returns the current snapshot (rebuilding it if needed), or null when the dispatcher is off or failed
     * to build. Nothing has been visited when this returns or throws.
     */
    public HolderDispatchIndex<H> prepare(Collection<H> source, Object lock) {
        synchronized (lock) {
            return snapshot(source);
        }
    }

    /** Counts one finished event and schedules a rebuild if unsettled handlers were visited. */
    public void record(HolderDispatchIndex<H> snapshot, Object key) {
        synchronized (this) {
            events++;
            visited += snapshot.visitCount(key);
            fullPassEquivalent += snapshot.size();
            if (snapshot.unsettledCount() > 0) {
                rebuildNext = true;
            }
        }
    }

    private synchronized HolderDispatchIndex<H> snapshot(Collection<H> source) {
        if (disabled) {
            return null;
        }
        try {
            int mutationCount = mutations.get();
            int size = source.size();
            if (index == null || rebuildNext || mutationCount != builtMutations || size != builtSize) {
                if (index != null && builds > maxRebuilds) {
                    disabled = true;
                    index = null;
                    return null;
                }
                index = HolderDispatchIndex.build(source, classifier);
                builds++;
                builtMutations = mutationCount;
                builtSize = size;
                rebuildNext = false;
            }
            return index;
        } catch (RuntimeException | LinkageError failure) {
            disabled = true;
            index = null;
            return null;
        }
    }

    /** Counters for one diagnostic line. */
    public synchronized Summary summary() {
        return new Summary(disabled, builds, events, visited, fullPassEquivalent,
                index == null ? 0 : index.size(), index == null ? 0 : index.dynamicCount(),
                index == null ? 0 : index.bucketCount());
    }

    public record Summary(boolean disabled, int builds, long events, long visited, long fullPassEquivalent,
                          int handlers, int alwaysVisited, int buckets) {
    }
}
