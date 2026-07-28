package dev.hoyin1600p.vhaccelerator.client.cache;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Records configuration paths touched between the early fingerprint preload
 * and the stable model-cache validation point.
 */
final class ConfigFingerprintMonitor implements AutoCloseable {
    private final WatchService watcher;
    private final Map<WatchKey, WatchedDirectory> directories =
            new HashMap<>();
    private final Map<String, Path> roots;
    private boolean closed;

    private ConfigFingerprintMonitor(
            WatchService watcher,
            Map<String, Path> roots
    ) {
        this.watcher = watcher;
        this.roots = Map.copyOf(roots);
    }

    static ConfigFingerprintMonitor open(
            Map<String, Path> roots
    ) {
        WatchService watcher;
        try {
            watcher = FileSystems.getDefault().newWatchService();
        } catch (IOException | RuntimeException failure) {
            return null;
        }

        ConfigFingerprintMonitor monitor =
                new ConfigFingerprintMonitor(watcher, roots);
        try {
            for (Map.Entry<String, Path> entry :
                    roots.entrySet()) {
                if (Files.isDirectory(entry.getValue())) {
                    monitor.registerTree(
                            entry.getValue(),
                            entry.getValue(),
                            entry.getKey()
                    );
                } else if (Files.notExists(entry.getValue())) {
                    if (!monitor.registerMissingRoot(
                            entry.getValue(),
                            entry.getKey()
                    )) {
                        monitor.close();
                        return null;
                    }
                } else {
                    monitor.close();
                    return null;
                }
            }
            return monitor;
        } catch (IOException | RuntimeException failure) {
            monitor.close();
            return null;
        }
    }

    Path root(String label) {
        return roots.get(label);
    }

    ChangeSet drain() {
        if (closed) {
            return ChangeSet.full();
        }

        boolean fullRescan = false;
        TreeSet<ChangedPath> changed = new TreeSet<>();
        WatchKey key;
        while ((key = watcher.poll()) != null) {
            fullRescan |= consume(key, changed);
        }
        return new ChangeSet(fullRescan, Set.copyOf(changed));
    }

    ChangeSet awaitQuietChanges(
            long quietMillis,
            long maximumWaitMillis
    ) {
        if (closed) {
            return ChangeSet.full();
        }

        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(maximumWaitMillis);
        boolean fullRescan = false;
        TreeSet<ChangedPath> changed = new TreeSet<>();
        while (System.nanoTime() < deadline) {
            long remaining = deadline - System.nanoTime();
            long quietNanos = Math.min(
                    TimeUnit.MILLISECONDS.toNanos(quietMillis),
                    Math.max(1L, remaining)
            );
            WatchKey key;
            try {
                key = watcher.poll(
                        quietNanos,
                        TimeUnit.NANOSECONDS
                );
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return ChangeSet.full();
            }
            if (key == null) {
                break;
            }
            fullRescan |= consume(key, changed);
            while ((key = watcher.poll()) != null) {
                fullRescan |= consume(key, changed);
            }
            if (fullRescan) {
                break;
            }
        }
        return new ChangeSet(fullRescan, Set.copyOf(changed));
    }

    private boolean consume(
            WatchKey key,
            Set<ChangedPath> changed
    ) {
        boolean fullRescan = false;
        WatchedDirectory watched = directories.get(key);
        if (watched == null) {
            key.reset();
            return true;
        }

        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                fullRescan = true;
                continue;
            }
            Object context = event.context();
            if (!(context instanceof Path relativeName)) {
                fullRescan = true;
                continue;
            }

            Path absolute =
                    watched.directory.resolve(relativeName);
            if (watched.missingRootObserver) {
                if (absolute.equals(watched.root)) {
                    fullRescan = true;
                }
                continue;
            }
            if (kind == StandardWatchEventKinds.ENTRY_CREATE
                    && Files.isDirectory(absolute)) {
                fullRescan = true;
                try {
                    registerTree(
                            absolute,
                            watched.root,
                            watched.label
                    );
                } catch (IOException | RuntimeException failure) {
                    fullRescan = true;
                }
                continue;
            }
            if (kind == StandardWatchEventKinds.ENTRY_DELETE
                    && removedDirectory(absolute)) {
                fullRescan = true;
                continue;
            }

            String relative = normalizeRelative(
                    watched.root,
                    absolute
            );
            changed.add(new ChangedPath(
                    watched.label,
                    relative
            ));
        }
        if (!key.reset()) {
            directories.remove(key);
            fullRescan = true;
        }
        return fullRescan;
    }

    private boolean removedDirectory(Path removed) {
        for (WatchedDirectory watched : directories.values()) {
            if (watched.directory.equals(removed)
                    || watched.directory.startsWith(removed)) {
                return true;
            }
        }
        return false;
    }

    private void registerTree(
            Path directory,
            Path root,
            String label
    ) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isDirectory).toList()) {
                WatchKey key = path.register(
                        watcher,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY
                );
                directories.put(
                        key,
                        new WatchedDirectory(
                                path,
                                root,
                                label,
                                false
                        )
                );
            }
        }
    }

    private boolean registerMissingRoot(
            Path root,
            String label
    ) throws IOException {
        Path parent = root.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return false;
        }
        WatchKey key = parent.register(
                watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
        );
        if (directories.containsKey(key)) {
            return false;
        }
        directories.put(
                key,
                new WatchedDirectory(
                        parent,
                        root,
                        label,
                        true
                )
        );
        return Files.notExists(root);
    }

    private static String normalizeRelative(
            Path root,
            Path path
    ) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            watcher.close();
        } catch (IOException ignored) {
            // Validation is complete; no watcher resource remains useful.
        }
        directories.clear();
    }

    record ChangedPath(
            String label,
            String relative
    ) implements Comparable<ChangedPath> {
        @Override
        public int compareTo(ChangedPath other) {
            int byLabel = label.compareTo(other.label);
            return byLabel != 0
                    ? byLabel
                    : relative.compareTo(other.relative);
        }
    }

    record ChangeSet(
            boolean fullRescan,
            Set<ChangedPath> paths
    ) {
        private static ChangeSet full() {
            return new ChangeSet(true, Set.of());
        }
    }

    private record WatchedDirectory(
            Path directory,
            Path root,
            String label,
            boolean missingRootObserver
    ) {
    }
}
