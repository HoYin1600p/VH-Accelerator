package dev.hoyin1600p.vhaccelerator.diagnostics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Bounded per-bucket aggregation of attributed stack samples, reported as
 * one line per bucket. Not thread-safe; callers synchronize.
 */
public final class SampleBuckets {
    private static final int MAX_BUCKETS = 64;
    private static final int MAX_OWNERS = 8_192;
    private static final int MAX_DETAILS = 256;
    private static final int TOP_OWNERS = 8;
    private static final int TOP_FRAMES = 3;
    private static final int OWNERS_WITH_PATHS = 3;
    private static final int TOP_PATHS = 2;

    private final Map<String, Bucket> buckets = new HashMap<>();
    private final Map<String, Long> classesLoaded = new java.util.LinkedHashMap<>();

    /** Adds classes loaded (JVM-wide) during a sampling interval to a window. */
    public void addClassesLoaded(String window, long classes) {
        if (classes > 0 && (classesLoaded.containsKey(window) || classesLoaded.size() < MAX_BUCKETS)) {
            classesLoaded.merge(window, classes, Long::sum);
        }
    }

    public void add(String bucketName, StackTraceElement[] stack, long nanos) {
        if (stack.length == 0) {
            return;
        }
        Bucket bucket = buckets.get(bucketName);
        if (bucket == null) {
            if (buckets.size() >= MAX_BUCKETS) {
                return;
            }
            bucket = new Bucket();
            buckets.put(bucketName, bucket);
        }
        bucket.add(
                StackAttribution.owner(stack),
                stack[0].getClassName() + "." + stack[0].getMethodName(),
                StackAttribution.path(stack),
                StackAttribution.category(stack),
                nanos
        );
    }

    public boolean isEmpty() {
        return buckets.isEmpty();
    }

    public void clear() {
        buckets.clear();
        classesLoaded.clear();
    }

    /** Emits one line per bucket, sorted by bucket name. */
    public void report(String label, Consumer<String> sink) {
        List<Map.Entry<String, Bucket>> sorted = new ArrayList<>(buckets.entrySet());
        sorted.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, Bucket> entry : sorted) {
            sink.accept("[debug] " + label + " " + entry.getKey() + ": "
                    + entry.getValue().describe());
        }
        for (Map.Entry<String, Bucket> entry : sorted) {
            sink.accept("[debug] " + label + " class loading " + entry.getKey() + ": "
                    + entry.getValue().describeCategories());
        }
        if (!classesLoaded.isEmpty()) {
            StringBuilder line = new StringBuilder();
            classesLoaded.forEach((window, count) -> {
                if (!line.isEmpty()) {
                    line.append(", ");
                }
                line.append(window).append('=').append(count);
            });
            sink.accept("[debug] " + label + " classes loaded by window (JVM-wide): " + line);
        }
    }

    private static final class Bucket {
        private long totalNanos;
        private int samples;
        private final Map<String, Long> ownerNanos = new HashMap<>();
        private final Map<String, Map<String, Long>> frameNanos = new HashMap<>();
        private final Map<String, Map<String, Long>> pathNanos = new HashMap<>();
        private final Map<String, Long> categoryNanos = new java.util.TreeMap<>();

        private void add(String owner, String frame, String path, String category, long nanos) {
            totalNanos += nanos;
            samples++;
            categoryNanos.merge(category, nanos, Long::sum);
            if (!ownerNanos.containsKey(owner) && ownerNanos.size() >= MAX_OWNERS) {
                owner = "(other)";
            }
            ownerNanos.merge(owner, nanos, Long::sum);
            merge(frameNanos, owner, frame, nanos);
            merge(pathNanos, owner, path, nanos);
        }

        private static void merge(
                Map<String, Map<String, Long>> details,
                String owner,
                String key,
                long nanos
        ) {
            Map<String, Long> values = details.computeIfAbsent(owner, ignored -> new HashMap<>());
            if (values.containsKey(key) || values.size() < MAX_DETAILS) {
                values.merge(key, nanos, Long::sum);
            }
        }

        private String describe() {
            StringBuilder line = new StringBuilder()
                    .append('~').append(totalNanos / 1_000_000L)
                    .append(" ms over ").append(samples).append(" samples: ");
            List<Map.Entry<String, Long>> owners = sortedDescending(ownerNanos);
            for (int index = 0; index < Math.min(TOP_OWNERS, owners.size()); index++) {
                Map.Entry<String, Long> owner = owners.get(index);
                if (index > 0) {
                    line.append("; ");
                }
                line.append(owner.getKey()).append(' ')
                        .append(owner.getValue() / 1_000_000L).append(" ms (")
                        .append(owner.getValue() * 100L / Math.max(1L, totalNanos))
                        .append("%)")
                        .append(top(" [top: ", frameNanos.get(owner.getKey()), TOP_FRAMES));
                if (index < OWNERS_WITH_PATHS) {
                    line.append(top(" [paths: ", pathNanos.get(owner.getKey()), TOP_PATHS));
                }
            }
            return line.toString();
        }

        private String describeCategories() {
            long loading = 0L;
            for (Map.Entry<String, Long> entry : categoryNanos.entrySet()) {
                if (entry.getKey().startsWith("class-load:")) {
                    loading += entry.getValue();
                }
            }
            StringBuilder line = new StringBuilder()
                    .append('~').append(loading / 1_000_000L).append(" ms loading classes (")
                    .append(loading * 100L / Math.max(1L, totalNanos)).append("% of ~")
                    .append(totalNanos / 1_000_000L).append(" ms)");
            categoryNanos.forEach((category, nanos) -> line.append("; ")
                    .append(category).append(' ').append(nanos / 1_000_000L).append(" ms"));
            return line.toString();
        }

        private static String top(String prefix, Map<String, Long> values, int limit) {
            if (values == null || values.isEmpty()) {
                return "";
            }
            List<Map.Entry<String, Long>> sorted = sortedDescending(values);
            StringBuilder text = new StringBuilder(prefix);
            for (int index = 0; index < Math.min(limit, sorted.size()); index++) {
                if (index > 0) {
                    text.append(" | ");
                }
                text.append(sorted.get(index).getKey()).append(' ')
                        .append(sorted.get(index).getValue() / 1_000_000L).append(" ms");
            }
            return text.append(']').toString();
        }

        private static List<Map.Entry<String, Long>> sortedDescending(Map<String, Long> values) {
            List<Map.Entry<String, Long>> sorted = new ArrayList<>(values.entrySet());
            sorted.sort(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()));
            return sorted;
        }
    }
}
