package dev.hoyin1600p.vhaccelerator.bootstrap;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;

/** Immutable launch fallback, captured once before Forge attaches its live common config. */
public final class BootstrapCommonConfig {
    private static final Snapshot LAUNCH = new Snapshot(BootstrapCommonConfig::capture);

    private BootstrapCommonConfig() { }

    public static boolean bool(String section, String key, boolean defaultValue) {
        return LAUNCH.bool(section, key, defaultValue);
    }

    private static Map<List<String>, Boolean> capture() {
        // Migration must precede the first snapshot, not run once for every distribution lookup.
        ConfigMigration.migrateCommon();
        return captureFrom(FMLPaths.CONFIGDIR.get(), FMLEnvironment.dist == Dist.CLIENT);
    }

    /**
     * Moves stale old defaults first, so the very first launch after an upgrade already gates on
     * the new default, then reads the (migrated) common file.
     */
    static Map<List<String>, Boolean> captureFrom(Path directory, boolean physicalClient) {
        ConfigDefaultsRevision.apply(directory, physicalClient);
        return read(directory.resolve(ConfigMigration.COMMON_CONFIG));
    }

    static Map<List<String>, Boolean> read(Path path) {
        if (!Files.isRegularFile(path)) return Map.of();
        try (CommentedFileConfig config = CommentedFileConfig.of(path)) {
            config.load();
            Map<List<String>, Boolean> values = new HashMap<>();
            collect(config, List.of(), values);
            return Map.copyOf(values);
        }
    }

    private static void collect(UnmodifiableConfig config, List<String> prefix,
                                Map<List<String>, Boolean> values) {
        config.valueMap().forEach((key, value) -> {
            List<String> path = new ArrayList<>(prefix);
            path.add(key);
            if (value instanceof Boolean bool) values.put(List.copyOf(path), bool);
            else if (value instanceof UnmodifiableConfig nested) collect(nested, path, values);
        });
    }

    /** A separate capture object makes the read-once/default contract testable without game paths. */
    static final class Snapshot {
        private final Supplier<Map<List<String>, Boolean>> loader;
        private volatile Map<List<String>, Boolean> values;

        Snapshot(Supplier<Map<List<String>, Boolean>> loader) {
            this.loader = loader;
        }

        boolean bool(String section, String key, boolean defaultValue) {
            Map<List<String>, Boolean> captured = values;
            if (captured == null) {
                synchronized (this) {
                    captured = values;
                    if (captured == null) {
                        try {
                            captured = Map.copyOf(loader.get());
                        } catch (RuntimeException | LinkageError failure) {
                            LogManager.getLogger("VH Accelerator").warn(
                                    "Could not capture common bootstrap settings; using defaults until Forge loads the config", failure);
                            captured = Map.of();
                        }
                        values = captured;
                    }
                }
            }
            return captured.getOrDefault(List.of(section, key), defaultValue);
        }
    }
}
