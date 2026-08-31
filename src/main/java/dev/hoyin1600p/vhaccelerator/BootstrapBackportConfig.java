package dev.hoyin1600p.vhaccelerator;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Captures restart-bound backport options before Forge attaches the common
 * configuration. The immutable snapshot prevents one JVM launch from changing
 * ownership halfway through Mixin selection or startup.
 */
public final class BootstrapBackportConfig {
    private static volatile Map<BackportFeature, Boolean> launchValues;

    private BootstrapBackportConfig() {
    }

    public static boolean enabled(BackportFeature feature) {
        capture();
        return launchValues.get(feature);
    }

    public static synchronized void capture() {
        if (launchValues != null) {
            return;
        }
        launchValues = readLaunchValues();
    }

    static Map<BackportFeature, Boolean> resolveValues(
            java.util.function.Function<List<String>, Object> lookup
    ) {
        EnumMap<BackportFeature, Boolean> resolved =
                new EnumMap<>(BackportFeature.class);
        for (BackportFeature feature : BackportFeature.values()) {
            List<String> configPath = feature
                    == BackportFeature.RESOURCE_PACK_INDEXING
                    ? List.of("optimizations", feature.configKey())
                    : List.of("backports", feature.configKey());
            Object configured = lookup.apply(configPath);
            resolved.put(
                    feature,
                    configured instanceof Boolean enabled
                            ? enabled
                            : feature.defaultEnabled()
            );
        }
        return Map.copyOf(resolved);
    }

    private static Map<BackportFeature, Boolean> readLaunchValues() {
        Path configDirectory = FMLPaths.CONFIGDIR.get();
        Path configPath = configDirectory.resolve(ConfigMigration.COMMON_CONFIG);
        if (!Files.isRegularFile(configPath)) {
            configPath = configDirectory.resolve(
                    ConfigMigration.LEGACY_COMMON_CONFIG
            );
        }
        if (!Files.isRegularFile(configPath)) {
            return resolveValues(path -> null);
        }

        try (CommentedFileConfig config = CommentedFileConfig.of(configPath)) {
            config.load();
            return resolveValues(config::get);
        } catch (Exception exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not capture backport options from {}; all use their safe defaults",
                    configPath,
                    exception
            );
            return resolveValues(path -> null);
        }
    }
}
