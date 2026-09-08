package dev.hoyin1600p.vhaccelerator;

import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.level.block.state.BlockState;

public final class ParallelBlockStateInitializer {
    private static final AtomicBoolean COLLECTING = new AtomicBoolean();
    private static final ConcurrentLinkedQueue<BlockState> DEFERRED_STATES =
            new ConcurrentLinkedQueue<>();

    private ParallelBlockStateInitializer() {
    }

    public static void startCollecting() {
        DEFERRED_STATES.clear();
        COLLECTING.set(true);
    }

    public static boolean isCollecting() {
        return COLLECTING.get();
    }

    public static void collect(BlockState state) {
        DEFERRED_STATES.add(state);
    }

    public static void discardCollectedStates() {
        COLLECTING.set(false);
        DEFERRED_STATES.clear();
    }

    public static void flushParallel() {
        COLLECTING.set(false);
        List<BlockState> states = new ArrayList<>(DEFERRED_STATES);
        DEFERRED_STATES.clear();
        if (states.isEmpty()) {
            return;
        }

        long startedAt = System.nanoTime();
        SharedWorkers.forEach(states, BlockState::initCache);
        VHAccelerator.LOGGER.info(
                "Initialized {} BlockState caches in parallel in {} ms",
                states.size(),
                (System.nanoTime() - startedAt) / 1_000_000L
        );
    }
}
