package dev.hoyin1600p.vhaccelerator.spawn;

/**
 * Thread-confined hand-off between {@code PlayerList} and the
 * {@code ServerPlayer} constructor for the world-spawn search.
 *
 * <p>Vanilla 1.18.2 runs {@code ServerPlayer#fudgeSpawnLocation} in every
 * {@code ServerPlayer} constructor. The search loads chunks around the shared
 * spawn and probes up to {@code (2 * spawnRadius + 1)^2} columns, yet its
 * result is discarded whenever the player has saved data
 * ({@code PlayerList#placeNewPlayer} loads the saved position right after
 * construction) or a valid respawn point
 * ({@code PlayerList#respawn} calls {@code moveTo} on the new player when
 * {@code findRespawnPositionAndUseSpawnBlock} succeeds). The idea comes from
 * ServerCore's {@code optimizations/players} mixins (MIT); VHA reimplements
 * it for Forge 40 without copying code.
 *
 * <p>{@code PlayerList} decides before constructing the player whether the
 * search result would be discarded and arms {@link #skipNextConstruction}.
 * The constructor consumes the flag exactly once, so any other
 * {@code ServerPlayer} construction (fake players, mods constructing players
 * for offline data) keeps vanilla behaviour. The flag lives in a
 * {@link ThreadLocal} so a construction on another thread can never observe
 * a decision made for a different player.
 */
public final class ReturningPlayerSpawnSearch {
    private static final ThreadLocal<Boolean> SKIP_NEXT_CONSTRUCTION =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ReturningPlayerSpawnSearch() {
    }

    /**
     * Arms or disarms the skip for the next {@code ServerPlayer} constructor
     * on this thread.
     */
    public static void skipNextConstruction(boolean skip) {
        SKIP_NEXT_CONSTRUCTION.set(skip);
    }

    /**
     * Returns whether the current construction should skip the search and
     * disarms the flag, so exactly one constructor honours each decision.
     */
    public static boolean consumeSkip() {
        boolean skip = SKIP_NEXT_CONSTRUCTION.get();
        if (skip) {
            SKIP_NEXT_CONSTRUCTION.set(Boolean.FALSE);
        }
        return skip;
    }

    /** Disarms any decision that was not consumed by a constructor. */
    public static void clear() {
        SKIP_NEXT_CONSTRUCTION.set(Boolean.FALSE);
    }

    static boolean armedForTest() {
        return SKIP_NEXT_CONSTRUCTION.get();
    }
}
