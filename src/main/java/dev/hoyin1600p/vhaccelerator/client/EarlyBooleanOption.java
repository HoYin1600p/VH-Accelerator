package dev.hoyin1600p.vhaccelerator.client;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads a restart-bound option before Forge attaches its client config. */
final class EarlyBooleanOption {
    private EarlyBooleanOption() { }
    static boolean read(Path path, List<String> key, boolean fallback) {
        if (!Files.isRegularFile(path)) { return fallback; }
        try (CommentedFileConfig config = CommentedFileConfig.of(path)) {
            config.load();
            Object value = config.get(key);
            return value instanceof Boolean enabled ? enabled : fallback;
        }
    }
}
