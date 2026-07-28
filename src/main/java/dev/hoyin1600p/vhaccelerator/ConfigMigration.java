package dev.hoyin1600p.vhaccelerator;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Imports settings from the previous mod identity without deleting or
 * modifying the user's original files.
 */
public final class ConfigMigration {
    public static final String COMMON_CONFIG = "vhaccelerator-common.toml";
    public static final String CLIENT_CONFIG = "vhaccelerator-client.toml";

    static final String LEGACY_COMMON_CONFIG = "launchfastertoo-common.toml";
    private static final String LEGACY_CLIENT_CONFIG = "launchfastertoo-client.toml";

    private ConfigMigration() {
    }

    public static void migrateCommon() {
        copyIfNeeded(LEGACY_COMMON_CONFIG, COMMON_CONFIG);
    }

    public static void migrateClient() {
        copyIfNeeded(LEGACY_CLIENT_CONFIG, CLIENT_CONFIG);
    }

    private static void copyIfNeeded(String legacyName, String currentName) {
        Path configDirectory = FMLPaths.CONFIGDIR.get();
        Path legacyPath = configDirectory.resolve(legacyName);
        Path currentPath = configDirectory.resolve(currentName);
        if (!Files.isRegularFile(legacyPath) || Files.exists(currentPath)) {
            return;
        }

        try {
            Files.copy(legacyPath, currentPath, StandardCopyOption.COPY_ATTRIBUTES);
            VHAccelerator.LOGGER.info(
                    "Imported legacy settings into {}", currentName
            );
        } catch (FileAlreadyExistsException ignored) {
            // Another initialization path won the race; its copy is authoritative.
        } catch (IOException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not import legacy settings into {}", currentName, exception
            );
        }
    }
}
