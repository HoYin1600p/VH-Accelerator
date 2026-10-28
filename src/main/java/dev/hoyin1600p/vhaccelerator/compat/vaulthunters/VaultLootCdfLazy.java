package dev.hoyin1600p.vhaccelerator.compat.vaulthunters;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Turns the map Vault (or VHAPI) computes on first use into the structure a lazy distribution keeps.
 * This class never computes the distribution itself: the caller passes in whatever {@code CDF.compute()}
 * returned, so VHAPI's and Vault's arithmetic stay theirs.
 */
public final class VaultLootCdfLazy {
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");
    private static final int LOGGED_RESOLUTIONS = 6;
    private static final AtomicInteger RESOLVED = new AtomicInteger();
    private static final AtomicInteger COMPACTED = new AtomicInteger();
    private static final AtomicInteger KEPT_MAP = new AtomicInteger();
    private static final AtomicLong COMPUTE_NANOS = new AtomicLong();

    /**
     * Held around every deferred {@code compute()}, across all distributions. VHAPI's overwrite keeps a static,
     * unsynchronized hash map of coefficients that Vault's eager construction only ever touched from one thread;
     * deferring must not let two threads fill it at once. Each distribution still computes at most once.
     */
    public static final Object COMPUTE_LOCK = new Object();

    private VaultLootCdfLazy() { }

    /** Wraps the computed map; null for a null map so the original behaviour (a null map) is kept. */
    public static VaultLootCdfDistribution resolve(Long2DoubleMap computed, int samples, int parts, int packBits,
            long computeNanos) {
        if (computed == null) return null;
        VaultLootCdfDistribution result = null;
        try {
            result = VaultLootCdfCompact.tryCompact(computed, samples, parts, packBits);
        } catch (RuntimeException | LinkageError | OutOfMemoryError failure) {
            LOGGER.warn("Vault loot CDF compaction failed for {} rolls; keeping the computed map", samples, failure);
        }
        if (result == null) {
            result = new MapBacked(computed);
            KEPT_MAP.incrementAndGet();
        } else {
            COMPACTED.incrementAndGet();
        }
        COMPUTE_NANOS.addAndGet(computeNanos);
        int ordinal = RESOLVED.incrementAndGet();
        if (ordinal <= LOGGED_RESOLUTIONS) {
            LOGGER.info("Vault loot distribution computed on first use: {} rolls, {} outcomes, {}, {} ms on {}",
                    samples, computed.size(), result.compact() ? "compact array" : "kept the computed map",
                    computeNanos / 1_000_000L, Thread.currentThread().getName());
        }
        return result;
    }

    /** Counters for diagnostics and tests: resolved, compacted, kept as map, total compute nanoseconds. */
    public static long[] statistics() {
        return new long[]{RESOLVED.get(), COMPACTED.get(), KEPT_MAP.get(), COMPUTE_NANOS.get()};
    }

    /** The computed map, kept when compaction is unsupported or could not be proven. */
    private record MapBacked(Long2DoubleMap map) implements VaultLootCdfDistribution {
        @Override
        public double get(long packed) {
            return map.get(packed);
        }

        @Override
        public boolean compact() {
            return false;
        }
    }
}
