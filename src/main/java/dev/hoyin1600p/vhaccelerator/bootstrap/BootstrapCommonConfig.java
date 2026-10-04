package dev.hoyin1600p.vhaccelerator.bootstrap;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;

/** Reads a common-config boolean at mixin selection time, before Forge loads configs. */
public final class BootstrapCommonConfig {
    private BootstrapCommonConfig() {
    }

    public static boolean bool(String section, String key, boolean defaultValue) {
        Path path = FMLPaths.CONFIGDIR.get().resolve("vhaccelerator-common.toml");
        if (!Files.isRegularFile(path)) {
            return defaultValue;
        }
        try (CommentedFileConfig config = CommentedFileConfig.of(path)) {
            config.load();
            Object value = config.get(List.of(section, key));
            return value instanceof Boolean enabled ? enabled : defaultValue;
        } catch (RuntimeException failure) {
            LogManager.getLogger("VH Accelerator").warn(
                    "Could not read common setting {}.{}; using the default", section, key, failure);
            return defaultValue;
        }
    }
}
