package dev.hoyin1600p.vhaccelerator.bootstrap;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge keeps every value already present in a config file, so an option whose
 * default was changed keeps its old value for anyone whose file was written
 * before. Each {@link Change} names the revision that changed a default; on the
 * first launch with a newer revision, an option still holding its old default
 * moves to the new one, and a value the player set to anything else is kept.
 * The applied revision is recorded next to the configs, so later edits are
 * never touched again. A missing marker counts as revision 0. Runs before mixin
 * selection reads the common config (see {@link BootstrapCommonConfig}).
 *
 * <p>To flip a default later: bump {@link #REVISION}, append a {@link Change}
 * with that revision as {@code since}, and never edit or remove older rows.
 */
public final class ConfigDefaultsRevision {
    static final int REVISION = 4;
    static final String MARKER = "vhaccelerator-defaults-revision.txt";

    /** One changed default: revision that introduced it, file, section, key, old and new default. */
    record Change(int since, String file, String section, String key, boolean from, boolean to) {
    }

    static final List<Change> CHANGES = List.of(
            // 1.1.0: confirmed in the Remastered test pack, Wolds Vaults 0.34.1 and Asgard.
            new Change(1, ConfigMigration.COMMON_CONFIG, "backports",
                    "deduplicateResourceLocationNamespaces", false, true),
            new Change(1, ConfigMigration.CLIENT_CONFIG, "optimizations", "deferItemModelBaking", false, true),
            new Change(1, ConfigMigration.CLIENT_CONFIG, "optimizations", "deferBlockStateModelBaking", false, true),
            new Change(1, ConfigMigration.CLIENT_CONFIG, "optimizations", "skipBlockStateGraphLoading", false, true),
            // BuildingGadgets template compaction became default-on after a paired server run.
            new Change(2, ConfigMigration.COMMON_CONFIG, "compatibility",
                    "compactBuildingGadgetsTemplates", false, true),
            // WorldZones compaction became default-on after paired server and save/restart checks.
            new Change(3, ConfigMigration.COMMON_CONFIG, "compatibility",
                    "compactWorldZonesMemberships", false, true),
            // Mixin tree release became default-on after server soak and paired reload checks.
            new Change(4, ConfigMigration.COMMON_CONFIG, "optimizations",
                    "releaseMixinClassTrees", false, true)
    );

    private ConfigDefaultsRevision() {
    }

    public static void apply() {
        apply(FMLPaths.CONFIGDIR.get(), FMLEnvironment.dist == Dist.CLIENT);
    }

    /**
     * @param physicalClient false on a dedicated server, which never reads or writes the client file
     */
    static synchronized void apply(Path directory, boolean physicalClient) {
        Path marker = directory.resolve(MARKER);
        int applied = readRevision(marker);
        if (applied >= REVISION) {
            return;
        }
        List<String> files = physicalClient
                ? List.of(ConfigMigration.COMMON_CONFIG, ConfigMigration.CLIENT_CONFIG)
                : List.of(ConfigMigration.COMMON_CONFIG);
        for (String file : files) {
            Path path = directory.resolve(file);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            // Synchronous writes: the default async writer hangs close() forever when the file is read-only.
            try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                boolean changed = false;
                for (Change change : CHANGES) {
                    if (change.since() <= applied || !change.file().equals(file)) {
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
                // Unreadable, malformed or read-only: keep the file and the old behaviour, retry next launch.
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
