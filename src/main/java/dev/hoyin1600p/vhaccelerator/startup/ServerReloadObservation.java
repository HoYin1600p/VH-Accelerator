package dev.hoyin1600p.vhaccelerator.startup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

/** Startup-only observers: original executors still execute every task and barrier. */
public final class ServerReloadObservation {
    private static final AtomicLong GENERATIONS = new AtomicLong();

    private ServerReloadObservation() { }

    public static List<PreparableReloadListener> wrap(List<PreparableReloadListener> originals,
                                                     List<PreparableReloadListener> timed) {
        return StartupDiagnostics.get("reload.observe.wrap", () -> wrapUnchecked(originals, timed), timed);
    }

    private static List<PreparableReloadListener> wrapUnchecked(List<PreparableReloadListener> originals,
                                                               List<PreparableReloadListener> timed) {
        if (!ServerLaunchTimer.detailedActive() || originals.isEmpty()) {
            return timed;
        }
        long generation = GENERATIONS.incrementAndGet();
        ServerLaunchTimer.checkpoint("reload_start", "hook:ReloadableServerResources listener creation");
        var root = ServerLaunchTimer.beginSpan("reload.listeners", null, generation, null);
        StalledReloadReporter.shared().reloadStarted(generation);
        AtomicInteger remaining = new AtomicInteger(originals.size());
        AtomicBoolean packsObserved = new AtomicBoolean();
        List<PreparableReloadListener> result = new ArrayList<>(timed.size());
        for (int i = 0; i < timed.size(); i++) {
            String owner = ReloadListenerTimer.listenerName(originals.get(i));
            result.add(new Observed(timed.get(i), owner, generation, root, remaining, packsObserved,
                    originals.get(i).getClass().getName()));
        }
        return result;
    }

    private record Observed(PreparableReloadListener delegate, String owner, long generation,
                            ServerStartupReport.Span root, AtomicInteger remaining, AtomicBoolean packsObserved,
                            String listenerClass)
            implements PreparableReloadListener {
        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                                               ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler,
                                               Executor background, Executor game) {
            String rootId = root == null ? null : root.id();
            if (root != null && packsObserved.compareAndSet(false, true)) {
                // No additional scans or reads: enumerate only the manager's already-selected packs.
                StartupDiagnostics.run("reload.observe.packs", () -> {
                    var packs = resources.listPacks().limit(513).map(pack -> Map.of(
                            "name", pack.getName(), "implementation", pack.getClass().getName(),
                            "mutability", "unclassified; no archive immutability assumption")).toList();
                    ServerLaunchTimer.observation("reload." + generation + ".pack_precedence", Map.of(
                        "source", "ResourceManager.listPacks iteration order; not a filesystem scan",
                        "packs", packs.subList(0, Math.min(512, packs.size())),
                        "truncated", packs.size() > 512,
                        "fileCounts", "unavailable", "bytesRead", "unavailable",
                        "parseDuration", "not separable from listener task work",
                        "allocation", "external JFR required"));
                });
            }
            String stallKey = StalledReloadReporter.shared().prepareStarted(owner, listenerClass, generation);
            var listener = ServerLaunchTimer.beginSpan("reload.listener:" + owner, rootId, generation, owner);
            String parent = listener == null ? rootId : listener.id();
            var preparing = ServerLaunchTimer.beginSpan("reload.prepare", parent, generation, owner);
            AtomicReference<ServerStartupReport.Span> prepare = new AtomicReference<>(preparing);
            AtomicReference<ServerStartupReport.Span> wait = new AtomicReference<>();
            AtomicReference<ServerStartupReport.Span> apply = new AtomicReference<>();
            PreparationBarrier observedBarrier = new PreparationBarrier() {
                @Override
                public <T> CompletableFuture<T> wait(T prepared) {
                    ServerLaunchTimer.endSpan(prepare.getAndSet(null), null);
                    StalledReloadReporter.shared().prepareFinished(stallKey);
                    wait.set(ServerLaunchTimer.beginSpan("reload.barrier_wait", parent, generation, owner));
                    return barrier.wait(prepared).whenComplete((value, failure) -> {
                        ServerLaunchTimer.endSpan(wait.getAndSet(null), failure);
                        if (failure == null) {
                            apply.set(ServerLaunchTimer.beginSpan("reload.apply", parent, generation, owner));
                        }
                    });
                }
            };
            CompletableFuture<Void> future;
            try {
                future = delegate.reload(observedBarrier, resources, prepareProfiler, applyProfiler,
                        observeExecutor(background, "reload.prepare", parent, generation, owner),
                        observeExecutor(game, "reload.apply", parent, generation, owner));
            } catch (RuntimeException | Error failure) {
                finish(listener, prepare, wait, apply, stallKey, failure);
                throw failure;
            }
            return future.whenComplete((unused, failure) -> finish(listener, prepare, wait, apply, stallKey, failure));
        }

        private void finish(ServerStartupReport.Span listener,
                            AtomicReference<ServerStartupReport.Span> prepare,
                            AtomicReference<ServerStartupReport.Span> wait,
                            AtomicReference<ServerStartupReport.Span> apply, String stallKey,
                            Throwable failure) {
            StalledReloadReporter.shared().listenerFinished(stallKey);
            ServerLaunchTimer.endSpan(prepare.getAndSet(null), failure);
            ServerLaunchTimer.endSpan(wait.getAndSet(null), failure);
            ServerLaunchTimer.endSpan(apply.getAndSet(null), failure);
            ServerLaunchTimer.endSpan(listener, failure);
            boolean last = remaining.decrementAndGet() == 0;
            if (failure != null || last) {
                StalledReloadReporter.shared().reloadFinished();
            }
            if (last) {
                ServerLaunchTimer.endSpan(root, failure);
                ServerLaunchTimer.checkpoint("reload_complete", "proxy:all observed reload listeners completed");
            }
        }

        @Override
        public String getName() {
            return delegate.getName();
        }
    }

    private static Executor observeExecutor(Executor executor, String stage, String parent,
                                             long generation, String owner) {
        return command -> {
            var queued = ServerLaunchTimer.beginSpan(stage + ".queue_wait", parent, generation, owner);
            try {
                executor.execute(() -> {
                    ServerLaunchTimer.endSpan(queued, null);
                    var task = ServerLaunchTimer.beginSpan(stage + ".task", parent, generation, owner);
                    var previousContext = ServerLaunchTimer.enterTaskContext(task == null ? parent : task.id(), generation);
                    Throwable failed = null;
                    try {
                        command.run();
                    } catch (RuntimeException | Error failure) {
                        failed = failure;
                        throw failure;
                    } finally {
                        ServerLaunchTimer.restoreTaskContext(previousContext);
                        ServerLaunchTimer.endSpan(task, failed);
                    }
                });
            } catch (RuntimeException | Error failure) {
                ServerLaunchTimer.endSpan(queued, failure);
                throw failure;
            }
        };
    }
}
