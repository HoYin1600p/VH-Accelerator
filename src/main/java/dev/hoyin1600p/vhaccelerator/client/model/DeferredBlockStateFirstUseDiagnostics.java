package dev.hoyin1600p.vhaccelerator.client.model;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Debug-only first-use diagnostics for deferred block-state models, created
 * only when debug diagnostics are enabled at install. Each world session (a
 * join, rejoin, or dimension change, i.e. a new client level) logs at most
 * two snapshots: one at its first playable frame and one about five seconds
 * of real time later, if still in that level. Bake timings accumulate between
 * resets (install, previous snapshot, world exit), so the final snapshot shows
 * whether significant first-use baking continued after the first playable
 * frame. Snapshots read the registry's counters and never iterate or bake it.
 */
public final class DeferredBlockStateFirstUseDiagnostics {
    static final long FINAL_SNAPSHOT_DELAY_NANOS = 5_000_000_000L;
    /** Slowest bakes kept per interval. */
    static final int SLOWEST_LIMIT = 5;
    /** World sessions reported per registry, bounding log volume. */
    static final int MAX_SESSIONS = 16;

    public record SlowBake(Object key, long nanos) {
    }

    public record Snapshot(
            String phase,
            long sinceFirstFrameNanos,
            int selected,
            int bakedOnDemand,
            int failed,
            int unresolved,
            int sharedWaits,
            int retiredLookups,
            int intervalBakes,
            long intervalNanos,
            List<SlowBake> slowest
    ) {
        public boolean isFinal() {
            return sinceFirstFrameNanos > 0L;
        }

        public String describe() {
            StringBuilder text = new StringBuilder("Deferred block-state models at ")
                    .append(phase);
            if (isFinal()) {
                text.append(" (").append(sinceFirstFrameNanos / 1_000_000L)
                        .append(" ms after first playable frame)");
            }
            text.append(": ")
                    .append(selected).append(" selected, ")
                    .append(bakedOnDemand).append(" resolved on demand, ")
                    .append(failed).append(" failed, ")
                    .append(unresolved).append(" unresolved, ")
                    .append(sharedWaits).append(" shared waits, ")
                    .append(retiredLookups).append(" retired lookups; ")
                    .append(intervalBakes).append(" first-use bakes totalling ")
                    .append(intervalNanos / 1_000_000L).append(" ms ")
                    .append(isFinal()
                            ? "since the first playable frame"
                            : "before this frame (since install, world exit "
                                    + "or the previous snapshot)")
                    .append(", worst ")
                    .append(slowest.isEmpty() ? 0L : slowest.get(0).nanos() / 1_000L)
                    .append(" us");
            if (!slowest.isEmpty()) {
                text.append("; slowest:");
                for (SlowBake bake : slowest) {
                    text.append(' ').append(bake.key()).append(' ')
                            .append(bake.nanos() / 1_000L).append(" us");
                }
            }
            return text.toString();
        }
    }

    private final ConcurrentDeferredModelRegistry<?, ?> registry;

    // Interval aggregation; any bake thread. Guarded by this.
    private int intervalBakes;
    private long intervalNanos;
    private final Object[] slowKeys = new Object[SLOWEST_LIMIT];
    private final long[] slowNanos = new long[SLOWEST_LIMIT];
    private int slowCount;

    // Session lifecycle; render thread. Guarded by this.
    private WeakReference<Object> sessionLevel;
    private long firstFrameNanos;
    private boolean finalPending;
    private int sessions;

    public DeferredBlockStateFirstUseDiagnostics(
            ConcurrentDeferredModelRegistry<?, ?> registry
    ) {
        this.registry = registry;
    }

    /** Records one first-use bake, successful or failed. */
    public synchronized void recordBake(Object key, long nanos) {
        intervalBakes++;
        intervalNanos += nanos;
        if (slowCount == SLOWEST_LIMIT && nanos <= slowNanos[SLOWEST_LIMIT - 1]) {
            return;
        }
        // Insertion into a small array kept in descending order.
        int position = slowCount < SLOWEST_LIMIT ? slowCount++ : SLOWEST_LIMIT - 1;
        while (position > 0 && slowNanos[position - 1] < nanos) {
            slowNanos[position] = slowNanos[position - 1];
            slowKeys[position] = slowKeys[position - 1];
            position--;
        }
        slowNanos[position] = nanos;
        slowKeys[position] = key;
    }

    /**
     * Called on every playable world frame. Returns the snapshot due now, or
     * null. A new level starts a session and drops any final snapshot still
     * pending for the previous one, so a dimension change or rejoin never
     * produces a stale summary.
     */
    public synchronized Snapshot observeFrame(Object level, long nowNanos) {
        Object current = sessionLevel == null ? null : sessionLevel.get();
        if (level != current) {
            finalPending = false;
            sessionLevel = new WeakReference<>(level);
            if (sessions >= MAX_SESSIONS) {
                return null;
            }
            sessions++;
            firstFrameNanos = nowNanos;
            finalPending = true;
            return snapshot("first playable frame", 0L);
        }
        if (finalPending && nowNanos - firstFrameNanos >= FINAL_SNAPSHOT_DELAY_NANOS) {
            finalPending = false;
            return snapshot("final first-use snapshot",
                    Math.max(1L, nowNanos - firstFrameNanos));
        }
        return null;
    }

    /** World exit: cancels any pending snapshot and restarts the interval. */
    public synchronized void cancelSession() {
        sessionLevel = null;
        finalPending = false;
        resetInterval();
    }

    synchronized boolean finalPending() {
        return finalPending;
    }

    // Registry counters are atomics or its read lock; its locks never wait
    // on this object, since bakes record only after baking.
    private Snapshot snapshot(String phase, long sinceFirstFrameNanos) {
        List<SlowBake> slowest = new ArrayList<>(slowCount);
        for (int index = 0; index < slowCount; index++) {
            slowest.add(new SlowBake(slowKeys[index], slowNanos[index]));
        }
        Snapshot snapshot = new Snapshot(
                phase,
                sinceFirstFrameNanos,
                registry.initialDeferred(),
                registry.bakedOnDemand(),
                registry.failedBakes(),
                registry.unresolvedDeferred(),
                registry.sharedWaits(),
                registry.retiredLookups(),
                intervalBakes,
                intervalNanos,
                List.copyOf(slowest)
        );
        resetInterval();
        return snapshot;
    }

    private void resetInterval() {
        intervalBakes = 0;
        intervalNanos = 0L;
        for (int index = 0; index < slowCount; index++) {
            slowKeys[index] = null;
            slowNanos[index] = 0L;
        }
        slowCount = 0;
    }
}
