package dev.hoyin1600p.launchfastertoo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.Util;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.InactiveProfiler;

/**
 * A maintained equivalent of LaunchFaster's reload instance.
 *
 * <p>Minecraft 1.18.2 already begins every listener's preparation work before
 * serializing their apply stages. This class preserves that scheduling,
 * hardens progress bookkeeping, and records preparation timing.</p>
 */
public final class ParallelReloadInstance implements ReloadInstance {
    private final CompletableFuture<List<Void>> allDone;
    private final CompletableFuture<Unit> allPreparations = new CompletableFuture<>();
    private final Set<PreparableReloadListener> preparingListeners =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final int listenerCount;
    private final AtomicInteger startedReloads = new AtomicInteger();
    private final AtomicInteger finishedReloads = new AtomicInteger();
    private final AtomicInteger startedTasks = new AtomicInteger();
    private final AtomicInteger finishedTasks = new AtomicInteger();

    public ParallelReloadInstance(
            ResourceManager resourceManager,
            List<PreparableReloadListener> listeners,
            Executor backgroundExecutor,
            Executor gameExecutor,
            CompletableFuture<Unit> alsoWaitedFor
    ) {
        listenerCount = listeners.size();
        preparingListeners.addAll(listeners);
        startedTasks.incrementAndGet();
        alsoWaitedFor.whenComplete((unused, error) -> finishedTasks.incrementAndGet());

        long startedAt = System.nanoTime();
        LaunchFasterToo.LOGGER.info(
                "Starting resource reload with {} listener preparations in flight",
                listenerCount
        );

        if (listeners.isEmpty()) {
            allPreparations.complete(Unit.INSTANCE);
        }

        List<CompletableFuture<Void>> listenerFutures = new ArrayList<>(listenerCount);
        CompletableFuture<?> previousApply = alsoWaitedFor;

        for (PreparableReloadListener listener : listeners) {
            CompletableFuture<?> applyDependency = previousApply;
            PreparableReloadListener.PreparationBarrier barrier = new PreparableReloadListener.PreparationBarrier() {
                @Override
                public <T> CompletableFuture<T> wait(T preparedValue) {
                    gameExecutor.execute(() -> {
                        preparingListeners.remove(listener);
                        if (preparingListeners.isEmpty()) {
                            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
                            LaunchFasterToo.LOGGER.info(
                                    "All {} reload listeners prepared in {} ms",
                                    listenerCount,
                                    elapsedMillis
                            );
                            allPreparations.complete(Unit.INSTANCE);
                        }
                    });
                    return allPreparations.thenCombine(applyDependency, (unit, ignored) -> preparedValue);
                }
            };

            CompletableFuture<Void> listenerFuture = listener.reload(
                    barrier,
                    resourceManager,
                    InactiveProfiler.INSTANCE,
                    InactiveProfiler.INSTANCE,
                    command -> {
                        startedTasks.incrementAndGet();
                        backgroundExecutor.execute(() -> {
                            try {
                                command.run();
                            } finally {
                                finishedTasks.incrementAndGet();
                            }
                        });
                    },
                    command -> {
                        startedReloads.incrementAndGet();
                        gameExecutor.execute(() -> {
                            try {
                                command.run();
                            } finally {
                                finishedReloads.incrementAndGet();
                            }
                        });
                    }
            );
            listenerFutures.add(listenerFuture);
            previousApply = listenerFuture;
        }

        allDone = Util.sequenceFailFast(listenerFutures);
    }

    @Override
    public CompletableFuture<?> done() {
        return allDone;
    }

    @Override
    public float getActualProgress() {
        int prepared = listenerCount - preparingListeners.size();
        float completedWeight = finishedTasks.get() * 2.0F
                + finishedReloads.get() * 2.0F
                + prepared;
        float totalWeight = startedTasks.get() * 2.0F
                + startedReloads.get() * 2.0F
                + listenerCount;
        return totalWeight == 0.0F ? 1.0F : completedWeight / totalWeight;
    }
}

