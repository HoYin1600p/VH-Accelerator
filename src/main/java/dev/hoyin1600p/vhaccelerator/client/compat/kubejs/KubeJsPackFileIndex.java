package dev.hoyin1600p.vhaccelerator.client.compat.kubejs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * KubeJS 1802.5.5's resource pack answers {@code hasResource} with a disk
 * {@code Files.exists} check under {@code kubejs/assets} or {@code kubejs/data}.
 * Its pack sits above every mod, so each resource lookup of a reload pays for
 * one (0.7 s of the Wolds launch). This lists the pack's folder once per open
 * pack and answers the same checks from memory; the listing is dropped when
 * the pack closes at the end of the reload, so edits are seen by the next one.
 *
 * <p>Windows matches file names without case, so keys are compared the way
 * the platform does. Paths outside the listed folder, and any listing
 * failure, fall back to the real disk check.
 */
public final class KubeJsPackFileIndex {
    private static final boolean CASE_INSENSITIVE =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private final Path root;
    private final String rootKey;
    private final Set<String> entries;

    private KubeJsPackFileIndex(Path root, String rootKey, Set<String> entries) {
        this.root = root;
        this.rootKey = rootKey;
        this.entries = entries;
    }

    /** Lists {@code root}; null when it cannot be listed exactly (the caller then checks the disk). */
    public static KubeJsPackFileIndex create(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        Set<String> entries = new HashSet<>();
        if (Files.exists(normalized)) {
            try (Stream<Path> walk = Files.walk(normalized)) {
                for (Path path : (Iterable<Path>) walk::iterator) {
                    if (Files.isSymbolicLink(path)) {
                        // exists() follows links and the listing does not.
                        return null;
                    }
                    entries.add(key(path.toAbsolutePath().normalize()));
                }
            } catch (IOException | RuntimeException failure) {
                return null;
            }
        }
        return new KubeJsPackFileIndex(normalized, key(normalized), entries);
    }

    /** The answer {@code Files.exists(file)} would give, or null to ask the disk. */
    public Boolean exists(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            return null;
        }
        String key = key(normalized);
        if (!key.startsWith(rootKey)) {
            return null;
        }
        return entries.contains(key);
    }

    public static boolean exists(KubeJsPackFileIndex index, Path file, LinkOption... options) {
        if (index != null && options.length == 0) {
            Boolean known = index.exists(file);
            if (known != null) {
                return known;
            }
        }
        return Files.exists(file, options);
    }

    private static String key(Path path) {
        String value = path.toString();
        return CASE_INSENSITIVE ? value.toLowerCase(Locale.ROOT) : value;
    }
}
