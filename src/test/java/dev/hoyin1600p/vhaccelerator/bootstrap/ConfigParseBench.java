package dev.hoyin1600p.vhaccelerator.bootstrap;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.Path;

/** Offline timing of one NightConfig parse of a real vhaccelerator-common.toml. Not a test. */
public final class ConfigParseBench {
    private ConfigParseBench() {
    }

    public static void main(String[] arguments) {
        Path file = Path.of(arguments[0]);
        for (int parse = 1; parse <= 8; parse++) {
            long begin = System.nanoTime();
            try (CommentedFileConfig config = CommentedFileConfig.of(file)) {
                config.load();
                config.get(java.util.List.of("diagnostics", "compareMode"));
            }
            System.out.printf("parse %d: %.2f ms%n", parse, (System.nanoTime() - begin) / 1e6);
        }
    }
}
