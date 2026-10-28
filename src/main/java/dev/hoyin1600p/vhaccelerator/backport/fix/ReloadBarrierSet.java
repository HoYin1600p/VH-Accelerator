package dev.hoyin1600p.vhaccelerator.backport.fix;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe replacement for the listener set that vanilla's
 * {@code SimpleReloadInstance} keeps while resource reloads prepare.
 *
 * <p>Vanilla fills a plain {@code HashSet} and, in each preparation barrier
 * callback, removes the finished listener and completes the "all prepared"
 * future once the set is empty. A dedicated server runs those callbacks on
 * whichever worker thread arrives (its game executor is {@code Runnable::run}),
 * so concurrent {@code HashSet.remove} calls can lose an update. The set then
 * never becomes empty and the future never completes, which hangs startup with
 * every thread idle and no exception.
 *
 * <p>With a concurrent set each remove is atomic. The last remover always
 * observes an empty set after its own removal, so completion is reached; two
 * threads may both observe it empty, but completing a future twice is a no-op.
 */
public final class ReloadBarrierSet {
    private ReloadBarrierSet() {
    }

    public static <T> Set<T> copyOf(Iterable<? extends T> elements) {
        Set<T> set = ConcurrentHashMap.newKeySet();
        for (T element : elements) {
            set.add(element);
        }
        return set;
    }
}
