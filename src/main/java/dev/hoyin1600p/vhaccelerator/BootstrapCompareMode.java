package dev.hoyin1600p.vhaccelerator;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Captures Compare Mode before Forge attaches the common configuration.
 *
 * <p>Some client mixins can execute before the mod constructor. Reading the
 * Forge config value at that point returns its specification default and can
 * start an optimization even when Compare Mode is enabled on disk. This
 * snapshot deliberately remains stable for the launch so a run can never
 * switch between optimized and baseline behavior partway through startup.</p>
 */
final class BootstrapCompareMode {
    private static final List<String> CONFIG_PATH =
            List.of("diagnostics", "compareMode");
    private static volatile Boolean launchValue;

    private BootstrapCompareMode() {
    }

    static boolean enabled() {
        Boolean captured = launchValue;
        if (captured != null) {
            return captured;
        }

        synchronized (BootstrapCompareMode.class) {
            if (launchValue == null) {
                launchValue = readLaunchValue();
                if (launchValue) {
                    VHAccelerator.LOGGER.info(
                            "Compare Mode enabled from bootstrap config; "
                                    + "all VH Accelerator optimizations are suppressed"
                    );
                }
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
        Path configPath = configDirectory.resolve(ConfigMigration.COMMON_CONFIG);
        if (!Files.isRegularFile(configPath)) {
            configPath = configDirectory.resolve(
                    ConfigMigration.LEGACY_COMMON_CONFIG
            );
        }
        if (!Files.isRegularFile(configPath)) {
            return false;
        }

        try (CommentedFileConfig config = CommentedFileConfig.of(configPath)) {
            config.load();
            Object configured = config.get(CONFIG_PATH);
            return configured instanceof Boolean enabled && enabled;
        } catch (Exception exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not capture Compare Mode from {}; using its disabled default",
                    configPath,
                    exception
            );
            return false;
        }
    }
}
