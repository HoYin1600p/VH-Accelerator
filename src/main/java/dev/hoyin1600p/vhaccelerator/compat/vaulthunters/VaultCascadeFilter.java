package dev.hoyin1600p.vhaccelerator.compat.vaulthunters;

import iskallia.vault.core.world.data.tile.OrTilePredicate;
import iskallia.vault.core.world.data.tile.PartialBlockState;
import iskallia.vault.core.world.data.tile.PartialTile;
import iskallia.vault.core.world.data.tile.TilePredicate;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Vault Hunters' cascade modifiers (the cake vault's chest and coin cascades)
 * visit every block entity of each generated chunk, once per cascade and per
 * region touching the chunk, building each one's state and saved data before
 * testing their block filter. Vault ores are block entities and the cake
 * vault's ore modifier stacks with every cake, so a late mine room holds tens
 * of thousands of them: at 1,564 cakes single rooms took 80 to 122 s server
 * ticks. The scan now receives only positions whose block the filter can
 * accept.
 *
 * <p>For a rejected block Vault's scan only attached a placeholder
 * ("DUMMY") record to the chunk's pending block entity data and moved on.
 * Positions come from the chunk's block entities and pending records, so a
 * rejected position either has a block entity (which saving and loading use
 * instead of the placeholder) or already has a record, whose missing id Vault
 * would have set to the equally unloadable "DUMMY". Matching positions run
 * Vault's code unchanged.
 */
public final class VaultCascadeFilter {
    private VaultCascadeFilter() {
    }

    /** The positions whose block {@code filter} may accept, or {@code positions} when it cannot tell. */
    public static Set<BlockPos> candidates(TilePredicate filter, ChunkAccess chunk, Set<BlockPos> positions) {
        if (!decidedByState(filter)) {
            return positions;
        }
        Map<BlockState, Boolean> rejected = new IdentityHashMap<>();
        Set<BlockPos> candidates = new HashSet<>();
        for (BlockPos pos : positions) {
            BlockState state = chunk.getBlockState(pos);
            Boolean reject = rejected.get(state);
            if (reject == null) {
                reject = rejectsState(filter, state);
                rejected.put(state, reject);
            }
            if (!reject) {
                candidates.add(pos);
            }
        }
        return candidates;
    }

    /** Block filters (id and properties) and "or" lists of them; tags and tile groups are not. */
    static boolean decidedByState(TilePredicate filter) {
        if (filter instanceof PartialTile) {
            return true;
        }
        if (filter instanceof OrTilePredicate or) {
            for (TilePredicate child : or.getChildren()) {
                if (!decidedByState(child)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** True when {@code filter}, decided by state, rejects {@code state} whatever its data. */
    static boolean rejectsState(TilePredicate filter, BlockState state) {
        if (filter instanceof PartialTile tile) {
            return !tile.getState().isSubsetOf(PartialBlockState.of(state));
        }
        if (filter instanceof OrTilePredicate or) {
            for (TilePredicate child : or.getChildren()) {
                if (!rejectsState(child, state)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
