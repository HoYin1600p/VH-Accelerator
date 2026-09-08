package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ParallelBlockStateModelLocations {

    private ParallelBlockStateModelLocations() {
    }

    public static void prepare() {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.cacheBlockStateModelLocations
                )
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.parallelBlockStateModelLocations
                )) {
            return;
        }

        List<BlockState> uncached = new ArrayList<>();
        for (Block block : Registry.BLOCK) {
            for (BlockState state
                    : block.getStateDefinition().getPossibleStates()) {
                if (((BlockStateModelLocationHolder) state)
                        .vhaccelerator$getModelLocation() == null) {
                    uncached.add(state);
                }
            }
        }
        if (uncached.isEmpty()) {
            return;
        }

        long started = System.nanoTime();
        int workers = workerCount(uncached.size());
        AtomicInteger failures = new AtomicInteger();
        SharedWorkers.forEach(uncached, state -> prepareState(state, failures));

        report(
                uncached.size(),
                failures.get(),
                workers,
                started
        );
    }

    private static void prepareState(
            BlockState state,
            AtomicInteger failures
    ) {
        try {
            BlockModelShaper.stateToModelLocation(state);
        } catch (RuntimeException | LinkageError failure) {
            failures.incrementAndGet();
            VHAccelerator.LOGGER.debug(
                    "Canonical model location for {} "
                            + "will use Minecraft's discovery path",
                    state,
                    failure
            );
        }
    }

    private static void report(
            int stateCount,
            int failureCount,
            int workers,
            long started
    ) {
        VHAccelerator.LOGGER.info(
                "Prepared {} canonical block-state model locations "
                        + "with {} workers in {} ms [{} deferred]",
                stateCount - failureCount,
                workers,
                (System.nanoTime() - started) / 1_000_000L,
                failureCount
        );
    }


    private static int workerCount(int stateCount) {
        return Math.max(1, Math.min(SharedWorkers.budget().compute(), stateCount));
    }
}
