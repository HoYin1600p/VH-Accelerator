package dev.hoyin1600p.vhaccelerator.spawn;

import net.minecraft.server.level.ServerLevel;

/**
 * Implemented by {@code ServerPlayer} through
 * {@code ServerPlayerSpawnSearchMixin}; lets {@code PlayerList} run the
 * skipped world-spawn search when the saved data it expected turns out to be
 * unreadable.
 */
public interface SpawnSearchAccess {
    /** Whether this player's constructor skipped the world-spawn search. */
    boolean vha$spawnSearchSkipped();

    /**
     * Runs vanilla's {@code fudgeSpawnLocation} for this player and clears
     * the skipped state.
     */
    void vha$searchSpawnLocation(ServerLevel level);
}
