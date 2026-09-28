package dev.hoyin1600p.vhaccelerator;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge keeps every value already present in a config file, so an option whose
 * default was turned on stays off for anyone whose file was written before.
 * Once per revision, an option still holding its old default is moved to the
 * new one; a value the player changed away from the old default is kept. The
 * applied revision is recorded next to the configs, so later edits are never
 * touched again. Runs before mixin selection reads the common config.
 */
public final class ConfigDefaultsRevision {
    static final int REVISION = 1;
    static final String MARKER = "vhaccelerator-defaults-revision.txt";

    /** One changed default: file, section, key, old default, new default. */
    record Change(String file, String section, String key, boolean from, boolean to) {
    }

    static final List<Change> CHANGES = List.of(
            // 1.1.0: confirmed in CMA Remastered, Wolds Vaults 0.34.1 and Asgard.
            new Change(ConfigMigration.COMMON_CONFIG, "backports",
                    "deduplicateResourceLocationNamespaces", false, true),
            new Change(ConfigMigration.CLIENT_CONFIG, "optimizations", "deferItemModelBaking", false, true),
            new Change(ConfigMigration.CLIENT_CONFIG, "optimizations", "deferBlockStateModelBaking", false, true),
            new Change(ConfigMigration.CLIENT_CONFIG, "optimizations", "skipBlockStateGraphLoading", false, true)
    );

    private ConfigDefaultsRevision() {
    }

    public static void apply() {
        apply(FMLPaths.CONFIGDIR.get());
    }

    static synchronized void apply(Path directory) {
        Path marker = directory.resolve(MARKER);
        int applied = readRevision(marker);
        if (applied >= REVISION) {
            return;
        }
        for (String file : List.of(ConfigMigration.COMMON_CONFIG, ConfigMigration.CLIENT_CONFIG)) {
            Path path = directory.resolve(file);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try (CommentedFileConfig config = CommentedFileConfig.of(path)) {
                config.load();
                boolean changed = false;
                for (Change change : CHANGES) {
                    if (!change.file().equals(file)) {
                        continue;
                    }
                    List<String> key = List.of(change.section(), change.key());
                    Object value = config.get(key);
                    if (value instanceof Boolean current && current == change.from()) {
                        config.set(key, change.to());
                        changed = true;
                        VHAccelerator.LOGGER.info(
                                "Moved {}.{} in {} from the old default {} to the current default {}",
                                change.section(), change.key(), file, change.from(), change.to());
                    }
                }
                if (changed) {
                    config.save();
                }
            } catch (RuntimeException failure) {
                VHAccelerator.LOGGER.warn("Could not update defaults in {}; leaving it unchanged", file, failure);
                return;
            }
        }
        try {
            Files.writeString(marker, Integer.toString(REVISION), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            VHAccelerator.LOGGER.warn("Could not record the applied config defaults revision", failure);
        }
    }

    static int readRevision(Path marker) {
        try {
            return Files.isRegularFile(marker)
                    ? Integer.parseInt(Files.readString(marker, StandardCharsets.UTF_8).trim())
                    : 0;
        } catch (IOException | NumberFormatException failure) {
            return 0;
        }
    }
}
