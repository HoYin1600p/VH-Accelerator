package dev.hoyin1600p.vhaccelerator.client.model;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

/**
 * Debug-only screening tally of block-state {@code ModelBakery#loadTopLevel}
 * time, used to judge whether skipping block-state graph loading on a warm
 * start could be worth pursuing. It never classifies keys as certified: the
 * candidate subset is only the cheap {@link DeferredBlockStateBaking#eligibleKey}
 * predicate, so uncached candidate time is an upper bound on recoverable
 * work, not a savings estimate.
 *
 * <p>One instance lives for one {@code processLoading} call with profiling
 * on. Calls are timed inclusively on a small identity-matched stack, so a
 * nested call is counted in both its own and its caller's time. Frames left
 * behind by an exception are discarded when an outer call returns. No key is
 * retained after its call returns. Single-threaded, like model discovery.
 */
public final class BlockStateGraphLoadDiagnostics {
    static final int MAX_DEPTH = 32;

    private final Object[] keys = new Object[MAX_DEPTH];
    private final long[] starts = new long[MAX_DEPTH];
    private final boolean[] candidates = new boolean[MAX_DEPTH];
    private final boolean[] cached = new boolean[MAX_DEPTH];
    private int depth;

    private long allNanos;
    private int allCalls;
    private long cachedNanos;
    private int cachedCalls;
    private long candidateUncachedNanos;
    private int candidateUncachedCalls;
    private long candidateCachedNanos;
    private int candidateCachedCalls;
    private int nestedCalls;
    private int untimedCalls;

    /** The deferred-baking eligibility predicate, exactly. */
    public static boolean candidate(ResourceLocation location) {
        return DeferredBlockStateBaking.eligibleKey(location);
    }

    /** Call HEAD; {@code key} is matched by identity in {@link #finish}. */
    public void begin(
            Object key,
            boolean candidate,
            boolean alreadyCached,
            long now
    ) {
        if (depth > 0) {
            nestedCalls++;
        }
        if (depth == MAX_DEPTH) {
            untimedCalls++;
            return;
        }
        keys[depth] = key;
        starts[depth] = now;
        candidates[depth] = candidate;
        cached[depth] = alreadyCached;
        depth++;
    }

    /** Call RETURN; unmatched keys are ignored rather than mis-timed. */
    public void finish(Object key, long now) {
        int frame = depth - 1;
        while (frame >= 0 && keys[frame] != key) {
            frame--;
        }
        if (frame < 0) {
            return;
        }
        // Frames above the match never returned (thrown and caught inside).
        for (int abandoned = depth - 1; abandoned > frame; abandoned--) {
            keys[abandoned] = null;
            untimedCalls++;
        }
        long elapsed = now - starts[frame];
        allNanos += elapsed;
        allCalls++;
        if (cached[frame]) {
            cachedNanos += elapsed;
            cachedCalls++;
        }
        if (candidates[frame]) {
            if (cached[frame]) {
                candidateCachedNanos += elapsed;
                candidateCachedCalls++;
            } else {
                candidateUncachedNanos += elapsed;
                candidateUncachedCalls++;
            }
        }
        keys[frame] = null;
        depth = frame;
    }

    long allNanos() {
        return allNanos;
    }

    int allCalls() {
        return allCalls;
    }

    long cachedNanos() {
        return cachedNanos;
    }

    int cachedCalls() {
        return cachedCalls;
    }

    long candidateUncachedNanos() {
        return candidateUncachedNanos;
    }

    int candidateUncachedCalls() {
        return candidateUncachedCalls;
    }

    long candidateCachedNanos() {
        return candidateCachedNanos;
    }

    int candidateCachedCalls() {
        return candidateCachedCalls;
    }

    int nestedCalls() {
        return nestedCalls;
    }

    /** Overflowed, or begun but never returned; open frames included. */
    int untimedCalls() {
        return untimedCalls + depth;
    }

    /** One bounded summary line without model identifiers. */
    public String describe(long processLoadingNanos) {
        return String.format(
                Locale.ROOT,
                "Block-state top-level discovery (inclusive HEAD-to-RETURN; "
                        + "nested and overlapping, not additive): all %.1f ms"
                        + "/%d key(s) [already-cached %.1f ms/%d]; "
                        + "deferral-eligible uncached %.1f ms/%d (UPPER BOUND "
                        + "on recoverable work, not savings), eligible "
                        + "already-cached %.1f ms/%d; nested %d, untimed %d; "
                        + "processLoading wall %.1f ms (contains all of these)",
                millis(allNanos),
                allCalls,
                millis(cachedNanos),
                cachedCalls,
                millis(candidateUncachedNanos),
                candidateUncachedCalls,
                millis(candidateCachedNanos),
                candidateCachedCalls,
                nestedCalls,
                untimedCalls(),
                millis(processLoadingNanos)
        );
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }
}

