package dev.hoyin1600p.vhaccelerator.bootstrap;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.io.IOException;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
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

    public static final String LEGACY_COMMON_CONFIG = "launchfastertoo-common.toml";
    private static final String LEGACY_CLIENT_CONFIG = "launchfastertoo-client.toml";

    private ConfigMigration() {
    }

    public static void migrateCommon() {
        copyIfNeeded(LEGACY_COMMON_CONFIG, COMMON_CONFIG);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // This runs before Forge registers/writes the common specification defaults.
            migrateClient();
            try {
                migrateVaultLootCdf(FMLPaths.CONFIGDIR.get(), true);
            } catch (RuntimeException failure) {
                VHAccelerator.LOGGER.warn("Could not migrate the Vault loot setting to the common config", failure);
            }
            try {
                migrateShapeSettings(FMLPaths.CONFIGDIR.get(), true);
            } catch (RuntimeException failure) {
                VHAccelerator.LOGGER.warn("Could not move the voxel-shape settings to the common config", failure);
            }
        }
    }

    public static void migrateClient() {
        copyIfNeeded(LEGACY_CLIENT_CONFIG, CLIENT_CONFIG);
    }

    /** Promote the old client preference once; an explicit common value is authoritative. */
    static boolean migrateVaultLootCdf(Path directory, boolean physicalClient) {
        return promoteClientBoolean(directory, physicalClient, List.of("compatibility", "optimizeVaultLootCdf"));
    }

    /** The two voxel-shape settings moved from the client to the common config the same way. */
    static boolean migrateShapeSettings(Path directory, boolean physicalClient) {
        boolean promoted = promoteClientBoolean(directory, physicalClient,
                List.of("optimizations", "cacheLaunchVoxelShapes"));
        promoted |= promoteClientBoolean(directory, physicalClient,
                List.of("optimizations", "optimizeVoxelShapeMerging"));
        return promoted;
    }

    private static boolean promoteClientBoolean(Path directory, boolean physicalClient, List<String> option) {
        if (!physicalClient) return false;
        Path clientPath = directory.resolve(CLIENT_CONFIG);
        if (!Files.isRegularFile(clientPath)) return false;
        Path commonPath = directory.resolve(COMMON_CONFIG);
        // Synchronous writes: the default async writer hangs close() forever when the file is read-only.
        try (CommentedFileConfig common = CommentedFileConfig.builder(commonPath).sync().build();
             CommentedFileConfig client = CommentedFileConfig.builder(clientPath).sync().build()) {
            if (Files.isRegularFile(commonPath)) common.load();
            if (common.contains(option)) return false;
            client.load();
            Object previous = client.get(option);
            if (!(previous instanceof Boolean)) return false;
            common.set(option, previous);
            common.setComment(option, "Imported from the previous client setting; now applies on both physical sides.");
            common.save();
            return true;
        }
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
