package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraftforge.eventbus.ASMEventHandler;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBusInvokeDispatcher;
import net.minecraftforge.eventbus.api.IEventListener;

/**
 * Attributes significant ordered Forge event work without changing dispatch
 * order or allowing listeners to overlap.
 */
public final class LaunchEventProfiler {
    private static final long MIN_SAMPLE_NANOS = 100_000L;
    private static final int REPORT_LIMIT = 24;
    private static final Pattern INSTANCE_HASH =
            Pattern.compile("@[0-9a-fA-F]+");
    private static final Map<SampleKey, Timing> TIMINGS =
            new ConcurrentHashMap<>();
    private static final Map<String, Timing> STAGE_TIMINGS =
            new ConcurrentHashMap<>();
    private static final Map<IEventListener, String> LISTENER_NAMES =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final AtomicBoolean FINISHED =
            new AtomicBoolean();

    private LaunchEventProfiler() {
    }

    public static void invoke(
            IEventBusInvokeDispatcher dispatcher,
            IEventListener listener,
            Event event
    ) {
        if (!enabled()) {
            dispatcher.invoke(listener, event);
            return;
        }

        long started = System.nanoTime();
        try {
            dispatcher.invoke(listener, event);
        } finally {
            long elapsed = System.nanoTime() - started;
            if (elapsed >= MIN_SAMPLE_NANOS) {
                SampleKey key = new SampleKey(
                        event.getClass().getName(),
                        listenerName(listener)
                );
                TIMINGS.computeIfAbsent(
                        key,
                        ignored -> new Timing()
                ).add(elapsed);
            }
        }
    }

    public static void finish() {
        if (!FINISHED.compareAndSet(false, true)) {
            return;
        }

        reportStages();
        reportListeners();
        STAGE_TIMINGS.clear();
        TIMINGS.clear();
        LISTENER_NAMES.clear();
    }

    public static void recordStage(
            String stage,
            long elapsedNanos
    ) {
        if (elapsedNanos <= 0L || FINISHED.get()) {
            return;
        }
        STAGE_TIMINGS.computeIfAbsent(
                stage,
                ignored -> new Timing()
        ).add(elapsedNanos);
    }

    public static boolean enabled() {
        return !FINISHED.get()
                && !LaunchTimer.isFinished()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .profileClientLaunchPhases
                );
    }

    private static void reportStages() {
        if (STAGE_TIMINGS.isEmpty()) {
            VHAccelerator.LOGGER.warn(
                    "Forge mod-loading state profiler observed no launch stages"
            );
            return;
        }

        List<Map.Entry<String, Timing>> stages =
                new ArrayList<>(STAGE_TIMINGS.entrySet());
        stages.sort(Comparator.comparingLong(
                (Map.Entry<String, Timing> entry) ->
                        entry.getValue().nanos.sum()
        ).reversed());
        for (int index = 0; index < stages.size(); index++) {
            Map.Entry<String, Timing> entry = stages.get(index);
            Timing timing = entry.getValue();
            VHAccelerator.LOGGER.info(
                    "Forge mod-loading stage [{}] {}: {} ms total "
                            + "across {} transition(s)",
                    index + 1,
                    entry.getKey(),
                    millis(timing.nanos.sum()),
                    timing.calls.sum()
            );
        }
    }

    private static void reportListeners() {
        if (TIMINGS.isEmpty()) {
            VHAccelerator.LOGGER.warn(
                    "Forge launch-listener profiler observed no listeners"
            );
            return;
        }

        List<Map.Entry<SampleKey, Timing>> entries =
                new ArrayList<>(TIMINGS.entrySet());
        entries.sort(Comparator.comparingLong(
                (Map.Entry<SampleKey, Timing> entry) ->
                        entry.getValue().nanos.sum()
        ).reversed());

        long significantNanos = entries.stream()
                .mapToLong(entry -> entry.getValue().nanos.sum())
                .sum();
        VHAccelerator.LOGGER.info(
                "Significant ordered Forge launch listeners accumulated "
                        + "{} ms of inclusive time across {} "
                        + "event/listener pair(s); "
                        + "showing the slowest {}",
                millis(significantNanos),
                entries.size(),
                Math.min(REPORT_LIMIT, entries.size())
        );

        for (int index = 0;
             index < Math.min(REPORT_LIMIT, entries.size());
             index++) {
            Map.Entry<SampleKey, Timing> entry = entries.get(index);
            Timing timing = entry.getValue();
            VHAccelerator.LOGGER.info(
                    "Forge launch listener [{}] {} -> {}: {} ms total "
                            + "across {} call(s), max {} ms",
                    index + 1,
                    simpleName(entry.getKey().eventClass),
                    entry.getKey().listener,
                    millis(timing.nanos.sum()),
                    timing.calls.sum(),
                    millis(timing.maximumNanos)
            );
        }
    }

    private static String listenerName(IEventListener listener) {
        synchronized (LISTENER_NAMES) {
            return LISTENER_NAMES.computeIfAbsent(
                    listener,
                    LaunchEventProfiler::resolveListenerName
            );
        }
    }

    private static String resolveListenerName(
            IEventListener listener
    ) {
        String name;
        try {
            if (listener instanceof ASMEventHandler) {
                name = listener.toString();
            } else {
                String captured = capturedConsumerName(listener);
                name = captured == null
                        ? listener.listenerName()
                        : captured;
            }
        } catch (RuntimeException | LinkageError failure) {
            name = listener.getClass().getName();
        }
        return INSTANCE_HASH.matcher(name).replaceAll("");
    }

    private static String capturedConsumerName(
            IEventListener listener
    ) {
        for (Field field : listener.getClass().getDeclaredFields()) {
            if (!Consumer.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                if (!field.trySetAccessible()) {
                    continue;
                }
                Object value = field.get(listener);
                if (value instanceof Consumer<?>) {
                    return value.getClass().getName();
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
                // The listener's own stable class name remains usable.
            }
        }
        return null;
    }

    private static String simpleName(String className) {
        int separator = className.lastIndexOf('.');
        return separator < 0
                ? className
                : className.substring(separator + 1);
    }

    private static String millis(long nanos) {
        return String.format(
                Locale.ROOT,
                "%.1f",
                nanos / 1_000_000.0
        );
    }

    private record SampleKey(
            String eventClass,
            String listener
    ) {
    }

    private static final class Timing {
        private final LongAdder nanos = new LongAdder();
        private final LongAdder calls = new LongAdder();
        private volatile long maximumNanos;

        private void add(long elapsed) {
            nanos.add(elapsed);
            calls.increment();
            if (elapsed > maximumNanos) {
                synchronized (this) {
                    if (elapsed > maximumNanos) {
                        maximumNanos = elapsed;
                    }
                }
            }
        }
    }
}
