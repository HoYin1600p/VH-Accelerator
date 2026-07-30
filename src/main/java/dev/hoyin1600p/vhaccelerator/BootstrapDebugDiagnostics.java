package dev.hoyin1600p.vhaccelerator;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Captures the diagnostics master switch before Forge attaches the common
 * configuration so early launch profilers see the user's saved choice.
 */
final class BootstrapDebugDiagnostics {
    private static final List<String> CONFIG_PATH =
            List.of("diagnostics", "debug");
    private static volatile Boolean launchValue;

    private BootstrapDebugDiagnostics() {
    }

    static boolean enabled() {
        Boolean captured = launchValue;
        if (captured != null) {
            return captured;
        }

        synchronized (BootstrapDebugDiagnostics.class) {
            if (launchValue == null) {
                launchValue = readLaunchValue();
            }
            return launchValue;
        }
    }

    static synchronized void capture() {
        enabled();
    }

    static synchronized void set(boolean enabled) {
        launchValue = enabled;
    }

    private static boolean readLaunchValue() {
        Path configDirectory = FMLPaths.CONFIGDIR.get();
        Path configPath = configDirectory.resolve(
                ConfigMigration.COMMON_CONFIG
        );
        if (!Files.isRegularFile(configPath)) {
            configPath = configDirectory.resolve(
                    ConfigMigration.LEGACY_COMMON_CONFIG
            );
        }
        if (!Files.isRegularFile(configPath)) {
            return false;
        }

        try (CommentedFileConfig config =
                     CommentedFileConfig.of(configPath)) {
            config.load();
            Object configured = config.get(CONFIG_PATH);
            return configured instanceof Boolean enabled && enabled;
        } catch (Exception exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not capture the diagnostics setting from {}; "
                            + "using its disabled default",
                    configPath,
                    exception
            );
            return false;
        }
    }
}
