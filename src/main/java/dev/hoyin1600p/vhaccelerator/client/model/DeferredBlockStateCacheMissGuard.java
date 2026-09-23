package dev.hoyin1600p.vhaccelerator.client.model;

import java.util.Map;
import java.util.function.Function;

/**
 * Fails a deferred block-state bake closed when it would miss the bakery's
 * unbaked cache.
 *
 * <p>A deferred bake may run on any thread beside other bakery users, so it
 * must only read already-loaded graphs. A cache miss would enter vanilla's
 * unsynchronized {@code loadModel} and {@code loadingStack} path; the guard
 * throws {@link CacheMissException} first, and the registry logs the key and
 * serves the missing model. It only detects attempted misses: it does not
 * detect in-place mutation of a cached model, nor a concurrent removal after
 * the check.
 *
 * <p>The scope is per thread and holds the owning bakery, so a bake on one
 * thread never affects {@code getModel} elsewhere. Outside a scope a check is
 * one thread-local read; inside, it adds one cache lookup.
 */
public final class DeferredBlockStateCacheMissGuard {
    private static final ThreadLocal<Object> ACTIVE_OWNER = new ThreadLocal<>();

    private DeferredBlockStateCacheMissGuard() {
    }

    /**
     * Runs {@code bake} with the guard active for {@code owner} on this
     * thread, restoring any enclosing scope afterwards.
     */
    public static <K, V> V bake(
            Object owner,
            K key,
            Function<? super K, ? extends V> bake
    ) {
        if (owner == null) {
            throw new IllegalArgumentException("owner");
        }
        Object previous = ACTIVE_OWNER.get();
        ACTIVE_OWNER.set(owner);
        try {
            return bake.apply(key);
        } finally {
            if (previous == null) {
                ACTIVE_OWNER.remove();
            } else {
                ACTIVE_OWNER.set(previous);
            }
        }
    }

    /** Whether a guarded bake for {@code owner} is running on this thread. */
    public static boolean active(Object owner) {
        Object active = ACTIVE_OWNER.get();
        return active != null && active == owner;
    }

    /**
     * Called at the head of {@code ModelBakery#getModel}. Throws when a
     * guarded bake for {@code owner} asks for a key absent from the cache.
     */
    public static void check(
            Object owner,
            Map<?, ?> unbakedCache,
            Object location
    ) {
        Object active = ACTIVE_OWNER.get();
        if (active != null && active == owner
                && (location == null || !unbakedCache.containsKey(location))) {
            throw new CacheMissException(location);
        }
    }

    /** Lightweight: no stack trace; the message names the missing key. */
    public static final class CacheMissException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CacheMissException(Object location) {
            super(
                    "Deferred block-state bake needed unloaded model "
                            + location
                            + "; refusing to load it off the bake pass",
                    null,
                    false,
                    false
            );
        }
    }
}

