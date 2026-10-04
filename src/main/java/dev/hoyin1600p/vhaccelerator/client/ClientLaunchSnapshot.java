package dev.hoyin1600p.vhaccelerator.client;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.bootstrap.ConfigMigration;
import dev.hoyin1600p.vhaccelerator.client.profiling.LaunchTimer;
import dev.hoyin1600p.vhaccelerator.client.update.UpdateNoticeFilter;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * The client config values as saved on disk at launch. Until the launch timer
 * finishes, options read from this snapshot so a setting changed in-game (or
 * by Forge's later config load) cannot alter what the early loading path does.
 */
final class ClientLaunchSnapshot {
    private static volatile Map<List<String>, Boolean> launchBooleanSnapshot =
            Map.of();
    private static volatile UpdateNoticeFilter launchUpdateFilterSnapshot =
            UpdateNoticeFilter.CRITICAL;
    private static volatile boolean launchSnapshotCaptured;

    private ClientLaunchSnapshot() {
    }

    static void capture(ClientConfigValues values) {
        Path configPath = FMLPaths.CONFIGDIR.get()
                .resolve(ConfigMigration.CLIENT_CONFIG);
        Map<List<String>, Boolean> snapshot = new HashMap<>();
        UpdateNoticeFilter updateFilter = UpdateNoticeFilter.CRITICAL;

        if (Files.isRegularFile(configPath)) {
            try (CommentedFileConfig config = CommentedFileConfig.of(configPath)) {
                config.load();
                for (Field field : ClientConfigValues.class.getFields()) {
                    Object value = field.get(values);
                    if (!(value instanceof ForgeConfigSpec.BooleanValue booleanValue)) {
                        continue;
                    }

                    Object configured = config.get(booleanValue.getPath());
                    if (configured instanceof Boolean enabled) {
                        snapshot.put(List.copyOf(booleanValue.getPath()), enabled);
                    }
                }
                updateFilter = UpdateNoticeFilter.fromConfigValue(
                        config.get(values.updateNoticeFilter.getPath()),
                        UpdateNoticeFilter.CRITICAL
                );
            } catch (Exception exception) {
                VHAccelerator.LOGGER.warn(
                        "Could not capture the initial client configuration from {}",
                        configPath,
                        exception
                );
            }
        }

        launchBooleanSnapshot = Map.copyOf(snapshot);
        launchUpdateFilterSnapshot = updateFilter;
        launchSnapshotCaptured = true;
        if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            VHAccelerator.LOGGER.info(
                    "Captured {} client launch configuration values "
                            + "before the initial resource reload",
                    launchBooleanSnapshot.size()
            );
        }
    }

    static boolean launchValue(ForgeConfigSpec.BooleanValue value) {
        if (!LaunchTimer.isFinished() && launchSnapshotCaptured) {
            Boolean configured = launchBooleanSnapshot.get(value.getPath());
            if (configured != null) {
                return configured;
            }
        }
        return value.get();
    }

    static boolean launchValue(
            ForgeConfigSpec.BooleanValue value,
            boolean defaultValue
    ) {
        if (!LaunchTimer.isFinished() && launchSnapshotCaptured) {
            Boolean configured = launchBooleanSnapshot.get(value.getPath());
            return configured != null ? configured : defaultValue;
        }
        return value.get();
    }

    static boolean captured() {
        return launchSnapshotCaptured;
    }

    /** The update filter as saved at launch, or the live value once the launch is over. */
    static UpdateNoticeFilter updateNoticeFilter(ClientConfigValues values) {
        if (!LaunchTimer.isFinished() && launchSnapshotCaptured) {
            return launchUpdateFilterSnapshot;
        }
        return values.updateNoticeFilter.get();
    }
}
