package dev.hoyin1600p.vhaccelerator.startup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Bounded record of how long individual event listeners ran during server startup.
 *
 * <p>The recording path is two clock reads, a few adders and, only for an invocation slower than
 * the current Nth-slowest, one short lock. Names are resolved lazily, once, when a snapshot is
 * taken, so nothing on the hot path touches reflection or mod metadata. The class knows nothing
 * about Forge, so it is exercised with fake listeners in tests.
 */
public final class LifecycleListenerLedger {
    public static final int DEFAULT_TOP_LIMIT = 64;
    public static final int DEFAULT_SLOT_LIMIT = 8192;
    public static final int MOD_LIMIT = 64;
    static final String UNKNOWN_MOD = "unknown";

    private final long originNanos;
    private final int topLimit;
    private final int slotLimit;
    private final ConcurrentLinkedQueue<Slot> slots = new ConcurrentLinkedQueue<>();
    private final AtomicInteger slotCount = new AtomicInteger();
    private final AtomicInteger refusedSlots = new AtomicInteger();
    private final LongAdder invocations = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final Object topLock = new Object();
    private final List<Sample> top = new ArrayList<>();
    private volatile long topFloorNanos;

    /** @param originNanos {@link System#nanoTime()} value that start offsets are measured from. */
    public LifecycleListenerLedger(long originNanos) {
        this(originNanos, DEFAULT_TOP_LIMIT, DEFAULT_SLOT_LIMIT);
    }

    LifecycleListenerLedger(long originNanos, int topLimit, int slotLimit) {
        if (topLimit < 1 || slotLimit < 1) {
            throw new IllegalArgumentException("Ledger limits must be positive");
        }
        this.originNanos = originNanos;
        this.topLimit = topLimit;
        this.slotLimit = slotLimit;
    }

    /** Names a listener once; resolved lazily at snapshot time. */
    public record Described(String mod, String listener) { }

    /** Totals for one wrapped listener on one event type. */
    public static final class Slot {
        private final String eventType;
        private final Supplier<Described> resolver;
        private final AtomicLong calls = new AtomicLong();
        private final AtomicLong total = new AtomicLong();
        private final AtomicLong max = new AtomicLong();
        private volatile Described described;

        private Slot(String eventType, Supplier<Described> resolver) {
            this.eventType = eventType;
            this.resolver = resolver;
        }

        Described described() {
            Described cached = described;
            if (cached == null) {
                try {
                    cached = resolver.get();
                } catch (RuntimeException | LinkageError | AssertionError failure) {
                    cached = null;
                }
                if (cached == null) cached = new Described(UNKNOWN_MOD, "unresolved listener");
                described = cached;
            }
            return cached;
        }
    }

    private record Sample(Slot slot, String thread, long startNanos, long durationNanos) { }

    /** @return the slot to record into, or null when the slot budget is exhausted. */
    public Slot register(String eventType, Supplier<Described> resolver) {
        if (slotCount.incrementAndGet() > slotLimit) {
            slotCount.decrementAndGet();
            refusedSlots.incrementAndGet();
            return null;
        }
        Slot slot = new Slot(eventType, resolver);
        slots.add(slot);
        return slot;
    }

    /** Never throws for well-formed input; durations below zero are clamped. */
    public void record(Slot slot, String thread, long startNanos, long endNanos) {
        long duration = Math.max(0L, endNanos - startNanos);
        slot.calls.incrementAndGet();
        slot.total.addAndGet(duration);
        long previous = slot.max.get();
        while (duration > previous && !slot.max.compareAndSet(previous, duration)) {
            previous = slot.max.get();
        }
        invocations.increment();
        totalNanos.add(duration);
        if (duration <= topFloorNanos) return;
        synchronized (topLock) {
            if (top.size() < topLimit) {
                top.add(new Sample(slot, thread, startNanos, duration));
            } else {
                int weakest = 0;
                for (int i = 1; i < top.size(); i++) {
                    if (top.get(i).durationNanos() < top.get(weakest).durationNanos()) weakest = i;
                }
                if (duration <= top.get(weakest).durationNanos()) return;
                top.set(weakest, new Sample(slot, thread, startNanos, duration));
            }
            if (top.size() >= topLimit) {
                long floor = Long.MAX_VALUE;
                for (Sample sample : top) floor = Math.min(floor, sample.durationNanos());
                topFloorNanos = floor;
            }
        }
    }

    public record Entry(String eventType, String mod, String listener, String thread,
                        long startOffsetNanos, long durationNanos) { }

    public record ModTotal(String mod, int listeners, long invocations, long totalNanos, long maxNanos) { }

    public record Snapshot(int wrappedListeners, int refusedListeners, long invocations, long totalNanos,
                           List<Entry> slowest, List<ModTotal> perMod, boolean modsTruncated) { }

    /** Names are resolved here; call once at the end of startup, off the listener hot path. */
    public Snapshot snapshot() {
        List<Sample> samples;
        synchronized (topLock) {
            samples = new ArrayList<>(top);
        }
        samples.sort(Comparator.comparingLong(Sample::durationNanos).reversed()
                .thenComparingLong(Sample::startNanos));
        List<Entry> slowest = new ArrayList<>(samples.size());
        for (Sample sample : samples) {
            Described described = sample.slot().described();
            slowest.add(new Entry(sample.slot().eventType, described.mod(), described.listener(),
                    sample.thread(), sample.startNanos() - originNanos, sample.durationNanos()));
        }
        Map<String, long[]> byMod = new LinkedHashMap<>(); // listeners, invocations, total, max
        for (Slot slot : slots) {
            long calls = slot.calls.get();
            if (calls == 0) continue;
            long[] totals = byMod.computeIfAbsent(slot.described().mod(), key -> new long[4]);
            totals[0]++;
            totals[1] += calls;
            totals[2] += slot.total.get();
            totals[3] = Math.max(totals[3], slot.max.get());
        }
        List<ModTotal> mods = new ArrayList<>();
        byMod.forEach((mod, totals) -> mods.add(
                new ModTotal(mod, (int) totals[0], totals[1], totals[2], totals[3])));
        mods.sort(Comparator.comparingLong(ModTotal::totalNanos).reversed().thenComparing(ModTotal::mod));
        boolean truncated = mods.size() > MOD_LIMIT;
        List<ModTotal> bounded = truncated ? new ArrayList<>(mods.subList(0, MOD_LIMIT)) : mods;
        return new Snapshot(slotCount.get(), refusedSlots.get(), invocations.sum(), totalNanos.sum(),
                slowest, bounded, truncated);
    }

    static double millis(long nanos) {
        return Math.round(nanos / 1_000.0) / 1_000.0;
    }

    /** Scalar, bounded values for the startup report. */
    public static Map<String, Object> toReport(Snapshot snapshot) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemaVersion", 1);
        out.put("wrappedListeners", snapshot.wrappedListeners());
        out.put("refusedListeners", snapshot.refusedListeners());
        out.put("invocations", snapshot.invocations());
        out.put("totalMillis", millis(snapshot.totalNanos()));
        out.put("note", "Durations are wall-clock time inside each listener, including time it waited; "
                + "listeners registered after the wrap points are not covered.");
        List<Map<String, Object>> slowest = new ArrayList<>();
        for (Entry entry : snapshot.slowest()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("event", entry.eventType());
            row.put("mod", entry.mod());
            row.put("listener", entry.listener());
            row.put("thread", entry.thread());
            row.put("startOffsetMillis", millis(entry.startOffsetNanos()));
            row.put("durationMillis", millis(entry.durationNanos()));
            slowest.add(row);
        }
        out.put("slowest", slowest);
        List<Map<String, Object>> mods = new ArrayList<>();
        for (ModTotal mod : snapshot.perMod()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("mod", mod.mod());
            row.put("listeners", mod.listeners());
            row.put("invocations", mod.invocations());
            row.put("totalMillis", millis(mod.totalNanos()));
            row.put("maxMillis", millis(mod.maxNanos()));
            mods.add(row);
        }
        out.put("perMod", mods);
        out.put("perModTruncated", snapshot.modsTruncated());
        return out;
    }

    /** One multi-line INFO message naming the slowest listeners. */
    public static String logBlock(Snapshot snapshot, int count) {
        StringBuilder text = new StringBuilder(512);
        text.append(String.format(Locale.ROOT,
                "Lifecycle listener ledger: %d listeners wrapped, %d invocations, %.1f ms total. "
                        + "%d slowest listeners:",
                snapshot.wrappedListeners(), snapshot.invocations(),
                snapshot.totalNanos() / 1_000_000.0, Math.min(count, snapshot.slowest().size())));
        int rank = 0;
        for (Entry entry : snapshot.slowest()) {
            if (rank >= count) break;
            rank++;
            text.append(String.format(Locale.ROOT, "%n  %2d. %10.1f ms  %s  %s  %s  [%s] at +%.0f ms",
                    rank, entry.durationNanos() / 1_000_000.0, entry.mod(), entry.eventType(),
                    entry.listener(), entry.thread(), entry.startOffsetNanos() / 1_000_000.0));
        }
        return text.toString();
    }
}
