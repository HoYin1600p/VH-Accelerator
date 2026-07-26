package dev.hoyin1600p.launchfastertoo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.Util;
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
        int parallelism = Math.max(1, Math.min(
                Runtime.getRuntime().availableProcessors(),
                states.size()
        ));
        int batchSize = Math.max(1, (states.size() + parallelism - 1) / parallelism);
        List<CompletableFuture<Void>> tasks = new ArrayList<>();

        for (int start = 0; start < states.size(); start += batchSize) {
            int from = start;
            int to = Math.min(start + batchSize, states.size());
            tasks.add(CompletableFuture.runAsync(() -> {
                for (int index = from; index < to; index++) {
                    states.get(index).initCache();
                }
            }, Util.backgroundExecutor()));
        }

        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        LaunchFasterToo.LOGGER.info(
                "Initialized {} BlockState caches in parallel in {} ms",
                states.size(),
                (System.nanoTime() - startedAt) / 1_000_000L
        );
    }
}

