package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.IEventListener;

/**
 * Temporary, low-overhead diagnostics for the client-side work between
 * creating the multiplayer player and rendering the first usable world frame.
 *
 * <p>Every Forge recipe/tag listener is measured. To keep the log readable,
 * listener samples are aggregated by their source mod jar before reporting.
 */
public final class ClientConnectionProfiler {
    private static final long OWNER_REPORT_NANOS = 50_000L;
    private static final Pattern ASM_TARGET = Pattern.compile(
            "^ASM: (?:class )?([A-Za-z0-9_.$]+)(?:@[0-9a-fA-F]+)?\\s"
    );
    private static final Pattern JAR_NAME = Pattern.compile(
            "([^/\\\\!]+?\\.jar)",
            Pattern.CASE_INSENSITIVE
    );

    private static boolean active;
    private static boolean playerReady;
    private static boolean firstWorldRenderClaimed;
    private static boolean firstWorldRenderInProgress;
    private static long attempt;
    private static long playerReadyNanos;
    private static int chunkPackets;
    private static long chunkPacketNanos;
    private static long slowestChunkPacketNanos;
    private static final Map<String, PacketStats> CUSTOM_PAYLOADS =
            new HashMap<>();

    private ClientConnectionProfiler() {
    }

    public static synchronized void beginConnection(long connectionAttempt) {
        active = true;
        playerReady = false;
        firstWorldRenderClaimed = false;
        firstWorldRenderInProgress = false;
        attempt = connectionAttempt;
        playerReadyNanos = -1L;
        chunkPackets = 0;
        chunkPacketNanos = 0L;
        slowestChunkPacketNanos = 0L;
        CUSTOM_PAYLOADS.clear();
    }

    public static synchronized void markPlayerReady() {
        if (!active || playerReady) {
            return;
        }
        playerReady = true;
        playerReadyNanos = System.nanoTime();
    }

    public static synchronized void cancel() {
        active = false;
        playerReady = false;
        firstWorldRenderClaimed = false;
        firstWorldRenderInProgress = false;
        CUSTOM_PAYLOADS.clear();
    }

    public static synchronized boolean isActive() {
        return active;
    }

    public static long startStage() {
        return isActive() ? System.nanoTime() : -1L;
    }

    public static void finishStage(String label, long startedNanos) {
        if (startedNanos < 0L) {
            return;
        }
        long elapsed = elapsed(startedNanos);
        VHAccelerator.LOGGER.info(
                "Connection phase '{}' completed in {} ms",
                label,
                formatMillis(elapsed)
        );
    }

    public static boolean postTimedEvent(
            IEventBus eventBus,
            Event event,
            String label
    ) {
        if (!isActive()) {
            return eventBus.post(event);
        }

        List<ListenerSample> samples = new ArrayList<>();
        long dispatchStarted = System.nanoTime();
        boolean cancelled = eventBus.post(event, (listener, dispatchedEvent) -> {
            long listenerStarted = System.nanoTime();
            try {
                listener.invoke(dispatchedEvent);
            } finally {
                samples.add(new ListenerSample(
                        listener,
                        elapsed(listenerStarted)
                ));
            }
        });
        long dispatchNanos = elapsed(dispatchStarted);
        reportEvent(label, samples, dispatchNanos);
        return cancelled;
    }

    public static synchronized void recordCustomPayload(
            String channel,
            long startedNanos
    ) {
        if (!active || startedNanos < 0L) {
            return;
        }
        long elapsed = elapsed(startedNanos);
        PacketStats stats = CUSTOM_PAYLOADS.computeIfAbsent(
                channel,
                ignored -> new PacketStats()
        );
        stats.count++;
        stats.totalNanos += elapsed;
        stats.maximumNanos = Math.max(stats.maximumNanos, elapsed);
    }

    public static synchronized void recordChunkPacket(long startedNanos) {
        if (!active || startedNanos < 0L) {
            return;
        }
        long elapsed = elapsed(startedNanos);
        chunkPackets++;
        chunkPacketNanos += elapsed;
        slowestChunkPacketNanos = Math.max(slowestChunkPacketNanos, elapsed);
    }

    public static synchronized long beginFirstWorldRender() {
        if (!active
                || !playerReady
                || firstWorldRenderClaimed) {
            return -1L;
        }
        firstWorldRenderClaimed = true;
        firstWorldRenderInProgress = true;
        return System.nanoTime();
    }

    public static synchronized long beginLevelRender() {
        return active && firstWorldRenderInProgress
                ? System.nanoTime()
                : -1L;
    }

    public static void finishLevelRender(long startedNanos) {
        finishStage("first LevelRenderer world pass", startedNanos);
    }

    public static synchronized void finishFirstWorldRender(
            long startedNanos
    ) {
        if (!active || startedNanos < 0L) {
            return;
        }
        long renderNanos = elapsed(startedNanos);
        long sincePlayerNanos = playerReadyNanos < 0L
                ? -1L
                : Math.max(0L, System.nanoTime() - playerReadyNanos);
        VHAccelerator.LOGGER.info(
                "Connection phase 'first GameRenderer world pass' completed "
                        + "in {} ms [{} ms since client player]",
                formatMillis(renderNanos),
                formatMillis(sincePlayerNanos)
        );
        reportPacketWork();
        VHAccelerator.LOGGER.info(
                "Client connection phase profile completed for attempt {}",
                attempt
        );
        active = false;
        playerReady = false;
        firstWorldRenderInProgress = false;
    }

    private static void reportEvent(
            String label,
            List<ListenerSample> samples,
            long dispatchNanos
    ) {
        VHAccelerator.LOGGER.info(
                "Forge {} dispatched {} measured listener(s) in {} ms",
                label,
                samples.size(),
                formatMillis(dispatchNanos)
        );

        Map<String, OwnerStats> owners = new HashMap<>();
        for (ListenerSample sample : samples) {
            String owner = ownerOf(sample.listener());
            OwnerStats stats = owners.computeIfAbsent(
                    owner,
                    ignored -> new OwnerStats()
            );
            stats.count++;
            stats.totalNanos += sample.elapsedNanos();
            if (sample.elapsedNanos() > stats.maximumNanos) {
                stats.maximumNanos = sample.elapsedNanos();
                stats.slowestListener = describe(sample.listener());
            }
        }

        owners.entrySet().stream()
                .filter(entry ->
                        entry.getValue().totalNanos >= OWNER_REPORT_NANOS)
                .sorted(Map.Entry.<String, OwnerStats>comparingByValue(
                        Comparator.comparingLong(
                                (OwnerStats value) -> value.totalNanos
                        ).reversed()
                ))
                .forEach(entry -> {
                    OwnerStats stats = entry.getValue();
                    VHAccelerator.LOGGER.info(
                            "Forge {} owner {}: {} listener(s), {} ms total "
                                    + "[slowest {} ms: {}]",
                            label,
                            entry.getKey(),
                            stats.count,
                            formatMillis(stats.totalNanos),
                            formatMillis(stats.maximumNanos),
                            stats.slowestListener
                    );
                });
    }

    private static synchronized void reportPacketWork() {
        VHAccelerator.LOGGER.info(
                "Initial chunk packet handling before first frame: {} packet(s), "
                        + "{} ms total [slowest {} ms]",
                chunkPackets,
                formatMillis(chunkPacketNanos),
                formatMillis(slowestChunkPacketNanos)
        );

        Map<String, PacketStats> ordered = new LinkedHashMap<>();
        CUSTOM_PAYLOADS.entrySet().stream()
                .sorted(Map.Entry.<String, PacketStats>comparingByValue(
                        Comparator.comparingLong(
                                (PacketStats value) -> value.totalNanos
                        ).reversed()
                ))
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        for (Map.Entry<String, PacketStats> entry : ordered.entrySet()) {
            PacketStats stats = entry.getValue();
            if (stats.totalNanos < OWNER_REPORT_NANOS) {
                continue;
            }
            VHAccelerator.LOGGER.info(
                    "Custom payload {} before first frame: {} packet(s), "
                            + "{} ms total [slowest {} ms]",
                    entry.getKey(),
                    stats.count,
                    formatMillis(stats.totalNanos),
                    formatMillis(stats.maximumNanos)
            );
        }
    }

    private static String describe(IEventListener listener) {
        String readable = String.valueOf(listener);
        if (readable.startsWith(listener.getClass().getName() + "@")) {
            return listener.listenerName();
        }
        return readable;
    }

    private static String ownerOf(IEventListener listener) {
        Class<?> ownerClass = targetClass(listener);
        String source = codeSource(ownerClass);
        if (source != null) {
            return source;
        }
        String className = ownerClass.getName();
        int first = className.indexOf('.');
        int second = first < 0 ? -1 : className.indexOf('.', first + 1);
        return second < 0 ? className : className.substring(0, second);
    }

    private static Class<?> targetClass(IEventListener listener) {
        Matcher matcher = ASM_TARGET.matcher(String.valueOf(listener));
        if (matcher.find()) {
            try {
                return Class.forName(
                        matcher.group(1),
                        false,
                        Thread.currentThread().getContextClassLoader()
                );
            } catch (ClassNotFoundException | LinkageError ignored) {
                // Fall through to the generated listener class.
            }
        }
        return listener.getClass();
    }

    private static String codeSource(Class<?> type) {
        try {
            CodeSource source = type.getProtectionDomain().getCodeSource();
            URL location = source == null ? null : source.getLocation();
            if (location == null) {
                return null;
            }
            String decoded = URLDecoder.decode(
                    location.toString(),
                    StandardCharsets.UTF_8
            );
            Matcher matcher = JAR_NAME.matcher(decoded);
            String name = null;
            while (matcher.find()) {
                name = matcher.group(1);
            }
            return name == null ? decoded : name;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static long elapsed(long startedNanos) {
        return Math.max(0L, System.nanoTime() - startedNanos);
    }

    private static String formatMillis(long nanos) {
        if (nanos < 0L) {
            return "unknown";
        }
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private record ListenerSample(
            IEventListener listener,
            long elapsedNanos
    ) {
    }

    private static final class OwnerStats {
        private int count;
        private long totalNanos;
        private long maximumNanos;
        private String slowestListener;
    }

    private static final class PacketStats {
        private int count;
        private long totalNanos;
        private long maximumNanos;
    }
}
