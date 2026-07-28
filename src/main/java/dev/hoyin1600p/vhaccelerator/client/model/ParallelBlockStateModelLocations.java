package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.Util;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ParallelBlockStateModelLocations {
    private static final int MIN_STATES_PER_WORKER = 4_096;

    private ParallelBlockStateModelLocations() {
    }

    public static void prepare() {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.VALUES
                        .cacheBlockStateModelLocations
                        .get()
                || !VHAcceleratorClientConfig.VALUES
                        .parallelBlockStateModelLocations
                        .get()) {
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
        int batchSize = Math.max(
                1,
                (uncached.size() + workers - 1) / workers
        );
        AtomicInteger failures = new AtomicInteger();
        List<CompletableFuture<Void>> tasks =
                new ArrayList<>(workers);
        for (int start = 0;
             start < uncached.size();
             start += batchSize) {
            int from = start;
            int to = Math.min(
                    start + batchSize,
                    uncached.size()
            );
            tasks.add(CompletableFuture.runAsync(() -> {
                for (int index = from; index < to; index++) {
                    BlockState state = uncached.get(index);
                    try {
                        BlockModelShaper
                                .stateToModelLocation(state);
                    } catch (RuntimeException | LinkageError failure) {
                        failures.incrementAndGet();
                        VHAccelerator.LOGGER.debug(
                                "Canonical model location for {} "
                                        + "will use Minecraft's "
                                        + "discovery path",
                                state,
                                failure
                        );
                    }
                }
            }, Util.backgroundExecutor()));
        }
        CompletableFuture.allOf(
                tasks.toArray(CompletableFuture[]::new)
        ).join();

        VHAccelerator.LOGGER.info(
                "Prepared {} canonical block-state model locations "
                        + "with {} workers in {} ms [{} deferred]",
                uncached.size() - failures.get(),
                tasks.size(),
                (System.nanoTime() - started) / 1_000_000L,
                failures.get()
        );
    }

    private static int workerCount(int stateCount) {
        int usefulWorkers = Math.max(
                1,
                (stateCount + MIN_STATES_PER_WORKER - 1)
                        / MIN_STATES_PER_WORKER
        );
        return Math.max(
                1,
                Math.min(
                        Runtime.getRuntime().availableProcessors(),
                        usefulWorkers
                )
        );
    }
}
