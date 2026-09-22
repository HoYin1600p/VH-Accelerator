package dev.hoyin1600p.vhaccelerator.client.model;

/**
 * Debug-only counters for deferred item models, created only when debug
 * diagnostics are enabled. Snapshots read the registry's constant-time
 * counters and never iterate or bake it. Bake timings accumulate per interval
 * between snapshots so launch, menu, and in-world first-use costs can be
 * compared separately.
 */
public final class DeferredItemModelDiagnostics {
    /** Individually logged first-use bakes per registry; the rest are aggregated. */
    static final int INDIVIDUAL_BAKE_LOG_LIMIT = 256;

    public record Snapshot(
            String phase,
            int selected,
            int unresolved,
            int baked,
            int failed,
            int removed,
            int intervalBakes,
            long intervalNanos,
            long slowestNanos,
            Object slowestKey
    ) {
        public String describe() {
            return "Deferred item models at " + phase + ": "
                    + selected + " selected, "
                    + unresolved + " unresolved, "
                    + baked + " baked on demand, "
                    + failed + " failed, "
                    + removed + " removed; "
                    + intervalBakes + " first-use bakes taking "
                    + intervalNanos / 1_000_000L + " ms since the previous "
                    + "snapshot (slowest "
                    + (slowestKey == null
                            ? "none"
                            : slowestKey + " " + slowestNanos / 1_000L + " us")
                    + ")";
        }
    }

    private String phase = "before first menu";
    private boolean menuReported;
    private boolean worldReported;
    private boolean inWorld;
    private int loggedBakes;
    private int intervalBakes;
    private long intervalNanos;
    private long slowestNanos;
    private Object slowestKey;

    /** Records one bake. Returns true while individual logging is allowed. */
    public synchronized boolean recordBake(Object key, long nanos) {
        intervalBakes++;
        intervalNanos += nanos;
        if (nanos >= slowestNanos) {
            slowestNanos = nanos;
            slowestKey = key;
        }
        if (loggedBakes < INDIVIDUAL_BAKE_LOG_LIMIT) {
            loggedBakes++;
            return true;
        }
        return false;
    }

    /** Current lifecycle phase, for labelling individual bakes. */
    public synchronized String phase() {
        return phase;
    }

    /**
     * Returns the snapshot label due for this frame, or null. The first menu
     * is reported once; world entry and exit are reported on each transition,
     * so a disconnect or server transfer is visible without baking anything.
     * A dimension change keeps a level loaded and is not a transition.
     */
    public synchronized String observe(boolean menuShown, boolean worldLoaded) {
        if (worldLoaded && !inWorld) {
            inWorld = true;
            phase = "in world";
            if (!worldReported) {
                worldReported = true;
                return "first world frame";
            }
            return "world rejoin";
        }
        if (!worldLoaded && inWorld) {
            inWorld = false;
            phase = "after leaving world";
            return "world exit";
        }
        if (menuShown && !menuReported && !worldLoaded) {
            menuReported = true;
            phase = "at menu";
            return "first menu";
        }
        return null;
    }

    public Snapshot snapshot(String label, DeferredModelRegistry<?, ?> registry) {
        // Bakes record while holding the registry lock, so registry counters
        // are read before, never while, holding this object's lock.
        int unresolved = registry.unresolvedDeferred();
        int baked = registry.bakedOnDemand();
        int failed = registry.failedBakes();
        int removed = registry.removedDeferred();
        synchronized (this) {
            Snapshot snapshot = new Snapshot(
                    label,
                    registry.initialDeferred(),
                    unresolved,
                    baked,
                    failed,
                    removed,
                    intervalBakes,
                    intervalNanos,
                    slowestNanos,
                    slowestKey
            );
            intervalBakes = 0;
            intervalNanos = 0L;
            slowestNanos = 0L;
            slowestKey = null;
            return snapshot;
        }
    }
}
