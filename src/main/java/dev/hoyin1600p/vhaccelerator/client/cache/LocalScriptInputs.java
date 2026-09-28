package dev.hoyin1600p.vhaccelerator.client.cache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Fingerprint inputs for pack scripts and script-supplied resources that sit
 * outside mods, configs, and resource packs: CraftTweaker {@code scripts/} and
 * KubeJS script, asset, data, and config folders. They can change recipes,
 * tags, fuel values, item subtypes, and models without changing any mod jar.
 *
 * <p>Each file contributes its relative path, size, and modification time.
 * Symbolic links are followed. KubeJS's generated {@code exported/} output is
 * not an input.
 */
final class LocalScriptInputs {
    static final List<String> ROOTS = List.of(
            "scripts",
            "kubejs/startup_scripts",
            "kubejs/server_scripts",
            "kubejs/client_scripts",
            "kubejs/assets",
            "kubejs/data",
            "kubejs/config"
    );
    static final int MAX_FILES = 100_000;
    private static final int MAX_DEPTH = 32;

    private LocalScriptInputs() {
    }

    /**
     * Returns the sorted inputs for every script root under
     * {@code gameDirectory}, or {@code null} if any root cannot be read
     * completely (including a link cycle or more than {@link #MAX_FILES}).
     */
    static List<String> collect(Path gameDirectory) {
        List<String> inputs = new ArrayList<>();
        int files = 0;
        for (String root : ROOTS) {
            Path directory = gameDirectory.resolve(root);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            List<Path> paths;
            try (Stream<Path> stream = Files.walk(
                    directory,
                    MAX_DEPTH,
                    FileVisitOption.FOLLOW_LINKS
            )) {
                paths = stream.filter(Files::isRegularFile)
                        .limit(MAX_FILES + 1L - files)
                        .sorted(Comparator.comparing(path -> relative(directory, path)))
                        .toList();
            } catch (IOException | UncheckedIOException failure) {
                return null;
            }
            files += paths.size();
            if (files > MAX_FILES) {
                return null;
            }
            for (Path path : paths) {
                try {
                    inputs.add(
                            "script="
                                    + root
                                    + "/"
                                    + relative(directory, path)
                                    + ":"
                                    + Files.size(path)
                                    + ":"
                                    + Files.getLastModifiedTime(path).toMillis()
                    );
                } catch (IOException failure) {
                    return null;
                }
            }
        }
        return inputs;
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }
}
