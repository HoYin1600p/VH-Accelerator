package dev.hoyin1600p.vhaccelerator.startup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.apache.logging.log4j.LogManager;

/**
 * Startup diagnostics only: while a server reload is unfinished, periodically names the reload
 * listeners that have not completed their prepare (or apply) stage. It observes; it never changes
 * the reload, and every entry point is contained by the {@link StartupDiagnostics} boundary.
 */
public final class StalledReloadReporter {
    static final long CHECK_PERIOD_SECONDS = 60L;
    static final int MAX_CHECKS = 10;
    private static final StalledReloadReporter SHARED = new StalledReloadReporter(
            CHECK_PERIOD_SECONDS, TimeUnit.SECONDS, MAX_CHECKS, System::nanoTime,
            message -> LogManager.getLogger("VH Accelerator").warn(message));

    private final long period;
    private final TimeUnit unit;
    private final int maxChecks;
    private final LongSupplier clock;
    private final Consumer<String> warn;
    private final ConcurrentHashMap<String, Entry> open = new ConcurrentHashMap<>();
    private final AtomicLong keys = new AtomicLong();
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;
    private int checks;
    private long activeGeneration = -1L;

    StalledReloadReporter(long period, TimeUnit unit, int maxChecks, LongSupplier clock, Consumer<String> warn) {
        this.period = period;
        this.unit = unit;
        this.maxChecks = maxChecks;
        this.clock = clock;
        this.warn = warn;
    }

    public static StalledReloadReporter shared() {
        return SHARED;
    }

    private enum Phase { PREPARE, APPLY }

    private static final class Entry {
        final String owner;
        final String listenerClass;
        final long generation;
        volatile Phase phase = Phase.PREPARE;
        final long startedNanos;
        volatile long phaseNanos;

        Entry(String owner, String listenerClass, long generation, long now) {
            this.owner = owner;
            this.listenerClass = listenerClass;
            this.generation = generation;
            this.startedNanos = now;
            this.phaseNanos = now;
        }
    }

    /** Called when a reload generation starts; schedules the periodic check. */
    public void reloadStarted(long generation) {
        StartupDiagnostics.run("reload.stalled.start", () -> {
            synchronized (this) {
                cancelLocked();
                open.clear();
                activeGeneration = generation;
                checks = 0;
                if (scheduler == null) {
                    scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                        Thread thread = new Thread(runnable, "VHA stalled-reload check");
                        thread.setDaemon(true);
                        return thread;
                    });
                }
                task = scheduler.scheduleWithFixedDelay(this::check, period, period, unit);
            }
        });
    }

    /** Returns a key identifying the listener, or null when tracking failed. */
    public String prepareStarted(String owner, String listenerClass, long generation) {
        return StartupDiagnostics.get("reload.stalled.prepare", () -> {
            String key = Long.toString(keys.incrementAndGet());
            open.put(key, new Entry(owner, listenerClass, generation, clock.getAsLong()));
            return key;
        }, null);
    }

    /** The listener reached its preparation barrier; only its apply stage remains. */
    public void prepareFinished(String key) {
        StartupDiagnostics.run("reload.stalled.barrier", () -> {
            Entry entry = key == null ? null : open.get(key);
            if (entry != null) {
                entry.phase = Phase.APPLY;
                entry.phaseNanos = clock.getAsLong();
            }
        });
    }

    public void listenerFinished(String key) {
        StartupDiagnostics.run("reload.stalled.finish", () -> {
            if (key != null) open.remove(key);
        });
    }

    /** The reload completed or failed: stop checking. */
    public void reloadFinished() {
        StartupDiagnostics.run("reload.stalled.complete", () -> {
            synchronized (this) {
                cancelLocked();
                open.clear();
                if (scheduler != null) {
                    scheduler.shutdown();
                    scheduler = null;
                }
            }
        });
    }

    private void cancelLocked() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    /** Runs on the scheduler thread; contained so a failure cannot reach game code or kill the schedule. */
    void check() {
        StartupDiagnostics.run("reload.stalled.check", () -> {
            int number;
            synchronized (this) {
                if (task == null) return;
                number = ++checks;
                if (checks >= maxChecks) cancelLocked();
            }
            long now = clock.getAsLong();
            List<Entry> preparing = new ArrayList<>();
            List<Entry> applying = new ArrayList<>();
            for (Entry entry : open.values()) {
                (entry.phase == Phase.PREPARE ? preparing : applying).add(entry);
            }
            if (preparing.isEmpty() && applying.isEmpty()) return;
            Comparator<Entry> oldest = Comparator.comparingLong((Entry e) -> e.startedNanos);
            preparing.sort(oldest);
            applying.sort(oldest);
            StringBuilder out = new StringBuilder("Server data reload still unfinished (check ")
                    .append(number).append('/').append(maxChecks).append(", generation ")
                    .append(activeGeneration).append("). Listeners whose reload.prepare is still open: ")
                    .append(preparing.size());
            for (Entry entry : preparing) {
                out.append("\n  - ").append(describe(entry)).append(", prepare open ")
                        .append(millis(now - entry.startedNanos)).append(" ms");
            }
            if (!applying.isEmpty()) {
                out.append("\nListeners finished prepare but not apply: ").append(applying.size());
                for (Entry entry : applying) {
                    out.append("\n  - ").append(describe(entry)).append(", apply open ")
                            .append(millis(now - entry.phaseNanos)).append(" ms");
                }
            }
            warn.accept(out.toString());
        });
    }

    private static String describe(Entry entry) {
        return "listener " + entry.listenerClass + " (owner " + entry.owner + ", generation " + entry.generation + ")";
    }

    private static long millis(long nanos) {
        return Math.max(0L, nanos) / 1_000_000L;
    }

    int openCount() {
        return open.size();
    }

    synchronized boolean scheduled() {
        return task != null;
    }
}
