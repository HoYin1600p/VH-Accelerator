package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Profiles the initial client resource reload without changing its scheduling.
 *
 * <p>Each listener receives the original executors and preparation barrier.
 * The wrapper observes only the barrier transition and the returned future, so
 * listeners retain their original thread-affinity and ordering guarantees.</p>
 */
public final class ClientReloadProfiler {
    private static final long REPORT_THRESHOLD_NANOS = 20_000_000L;
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();

    private ClientReloadProfiler() {
    }

    public static List<PreparableReloadListener> wrapInitialReload(
            List<PreparableReloadListener> listeners
    ) {
        if (!enabled()
                || LaunchTimer.isFinished()
                || listeners.isEmpty()
                || !ACTIVE.compareAndSet(false, true)) {
            return listeners;
        }

        ProfileSession session = new ProfileSession(listeners.size());
        List<PreparableReloadListener> wrapped =
                new ArrayList<>(listeners.size());
        for (int index = 0; index < listeners.size(); index++) {
            wrapped.add(new TimedReloadListener(
                    listeners.get(index),
                    index,
                    session
            ));
        }
        VHAccelerator.LOGGER.info(
                "Profiling {} initial client resource reload listeners",
                listeners.size()
        );
        return wrapped;
    }

    private static boolean enabled() {
        return VHAcceleratorClientConfig.launchValue(
                VHAcceleratorClientConfig.VALUES.profileClientLaunchPhases
        );
    }

    private static String listenerName(PreparableReloadListener listener) {
        String simpleName = listener.getClass().getSimpleName();
        return simpleName.isEmpty()
                ? listener.getClass().getName()
                : simpleName;
    }

    private static final class TimedReloadListener
            implements PreparableReloadListener {
        private final PreparableReloadListener delegate;
        private final int index;
        private final ProfileSession session;

        private TimedReloadListener(
                PreparableReloadListener delegate,
                int index,
                ProfileSession session
        ) {
            this.delegate = delegate;
            this.index = index;
            this.session = session;
        }

        @Override
        public CompletableFuture<Void> reload(
                PreparationBarrier barrier,
                ResourceManager resourceManager,
                ProfilerFiller preparationProfiler,
                ProfilerFiller reloadProfiler,
                Executor backgroundExecutor,
                Executor gameExecutor
        ) {
            ListenerTiming timing = new ListenerTiming(
                    index,
                    listenerName(delegate),
                    System.nanoTime()
            );
            PreparationBarrier observedBarrier = new PreparationBarrier() {
                @Override
                public <T> CompletableFuture<T> wait(T preparedValue) {
                    timing.preparedNanos.compareAndSet(
                            0L,
                            System.nanoTime()
                    );
                    return barrier.wait(preparedValue).thenApply(value -> {
                        timing.applyStartedNanos.compareAndSet(
                                0L,
                                System.nanoTime()
                        );
                        return value;
                    });
                }
            };

            CompletableFuture<Void> result;
            try {
                result = delegate.reload(
                        observedBarrier,
                        resourceManager,
                        preparationProfiler,
                        reloadProfiler,
                        backgroundExecutor,
                        gameExecutor
                );
            } catch (RuntimeException | Error failure) {
                timing.finishedNanos.set(System.nanoTime());
                session.complete(timing);
                throw failure;
            }
            return result.whenComplete((unused, failure) -> {
                timing.finishedNanos.compareAndSet(
                        0L,
                        System.nanoTime()
                );
                session.complete(timing);
            });
        }

        @Override
        public String getName() {
            return delegate.getName();
        }
    }

    private static final class ProfileSession {
        private final long startedNanos = System.nanoTime();
        private final int expected;
        private final AtomicInteger completed = new AtomicInteger();
        private final List<ListenerTiming> timings =
                java.util.Collections.synchronizedList(new ArrayList<>());

        private ProfileSession(int expected) {
            this.expected = expected;
        }

        private void complete(ListenerTiming timing) {
            timings.add(timing);
            if (completed.incrementAndGet() != expected) {
                return;
            }

            try {
                List<ListenerTiming> snapshot;
                synchronized (timings) {
                    snapshot = new ArrayList<>(timings);
                }
                snapshot.sort(Comparator.comparingLong(
                        ListenerTiming::totalNanos
                ).reversed());

                int reported = 0;
                for (ListenerTiming entry : snapshot) {
                    if (entry.measuredWorkNanos()
                            < REPORT_THRESHOLD_NANOS) {
                        continue;
                    }
                    VHAccelerator.LOGGER.info(
                            "Client reload listener {}: prepare {} ms, "
                                    + "apply {} ms, measured work {} ms, "
                                    + "completion {} ms",
                            entry.name,
                            formatMillis(entry.preparationNanos()),
                            formatMillis(entry.applicationNanos()),
                            formatMillis(entry.measuredWorkNanos()),
                            formatMillis(entry.totalNanos())
                    );
                    reported++;
                }
                VHAccelerator.LOGGER.info(
                        "Initial client resource reload completed in {} ms; "
                                + "{} of {} listeners performed at least "
                                + "20 ms of measured work",
                        formatMillis(System.nanoTime() - startedNanos),
                        reported,
                        expected
                );
            } finally {
                ACTIVE.set(false);
            }
        }
    }

    private static final class ListenerTiming {
        private final int index;
        private final String name;
        private final long startedNanos;
        private final AtomicLong preparedNanos = new AtomicLong();
        private final AtomicLong applyStartedNanos = new AtomicLong();
        private final AtomicLong finishedNanos = new AtomicLong();

        private ListenerTiming(
                int index,
                String name,
                long startedNanos
        ) {
            this.index = index;
            this.name = name + " [" + index + "]";
            this.startedNanos = startedNanos;
        }

        private long preparationNanos() {
            long prepared = preparedNanos.get();
            return prepared == 0L
                    ? 0L
                    : Math.max(0L, prepared - startedNanos);
        }

        private long applicationNanos() {
            long applyStarted = applyStartedNanos.get();
            long finished = finishedNanos.get();
            return applyStarted == 0L || finished == 0L
                    ? 0L
                    : Math.max(0L, finished - applyStarted);
        }

        private long totalNanos() {
            long finished = finishedNanos.get();
            return finished == 0L
                    ? 0L
                    : Math.max(0L, finished - startedNanos);
        }

        private long measuredWorkNanos() {
            return preparationNanos() + applicationNanos();
        }
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }
}
