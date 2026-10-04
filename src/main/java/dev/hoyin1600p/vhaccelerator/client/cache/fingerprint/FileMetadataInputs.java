package dev.hoyin1600p.vhaccelerator.client.cache.fingerprint;

import dev.hoyin1600p.vhaccelerator.util.PathNames;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** Size-and-timestamp inputs for the mod jars and resource packs on disk. */
final class FileMetadataInputs {
    private FileMetadataInputs() {
    }

    static void appendFlatMetadata(
            List<String> inputs,
            Path directory,
            String label
    ) {
        if (!Files.isDirectory(directory)) {
            inputs.add(label + "-directory-missing");
            return;
        }
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(Files::isRegularFile)
                    .filter(ActiveModFilePolicy::isJar)
                    .sorted(Comparator.comparing(path ->
                            path.getFileName().toString()))
                    .forEach(path -> appendMetadata(
                            inputs,
                            directory,
                            path,
                            label
                    ));
        } catch (IOException exception) {
            inputs.add(label + "-directory-read-failed");
        }
    }

    static void appendResourcePackMetadata(
            List<String> inputs,
            Path directory
    ) {
        if (!Files.isDirectory(directory)) {
            inputs.add("resourcepack-directory-missing");
            return;
        }
        try (Stream<Path> paths = Files.walk(
                directory,
                FileVisitOption.FOLLOW_LINKS
        )) {
            paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path ->
                            PathNames.relative(directory, path)))
                    .forEach(path -> appendMetadata(
                            inputs,
                            directory,
                            path,
                            "resourcepack"
                    ));
        } catch (IOException | UncheckedIOException exception) {
            // Never reuse caches against resource packs that were not read.
            inputs.add("resourcepack-directory-read-failed="
                    + UUID.randomUUID());
        }
    }

    static void appendMetadata(
            List<String> inputs,
            Path root,
            Path path,
            String label
    ) {
        try {
            inputs.add(
                    label
                            + "="
                            + PathNames.relative(root, path)
                            + ":"
                            + Files.size(path)
                            + ":"
                            + Files.getLastModifiedTime(path).toMillis()
            );
        } catch (IOException exception) {
            inputs.add(
                    label
                            + "-read-failed="
                            + PathNames.relative(root, path)
            );
        }
    }
}
