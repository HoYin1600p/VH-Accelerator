package dev.hoyin1600p.vhaccelerator.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Write-then-move helpers shared by the persistent caches. */
public final class AtomicFiles {
    private AtomicFiles() {
    }

    /** Writes the contents of a file to an output stream. */
    @FunctionalInterface
    public interface StreamWriter {
        void write(OutputStream output) throws IOException;
    }

    /**
     * Writes {@code target} through a sibling temporary file (the file name
     * plus {@link CacheFiles#TEMP_SUFFIX}) and moves it into place. The
     * target's directory must already exist. The temporary file is deleted
     * when writing or moving fails, and the failure is rethrown.
     */
    public static void write(Path target, StreamWriter writer) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + CacheFiles.TEMP_SUFFIX);
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                writer.write(output);
            }
            moveIntoPlace(temporary, target);
        } catch (Throwable failure) {
            // Any failure, including an Error, leaves no partial temporary file behind.
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The incomplete temporary file is harmless.
            }
            throw failure;
        }
    }

    /**
     * Replaces {@code target} with {@code source}, atomically when the file
     * system supports it and with a plain replacing move otherwise.
     */
    public static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
