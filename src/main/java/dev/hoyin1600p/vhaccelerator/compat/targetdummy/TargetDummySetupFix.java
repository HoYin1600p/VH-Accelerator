package dev.hoyin1600p.vhaccelerator.compat.targetdummy;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;

/** Common-side startup correction; no client or Target Dummy class dependencies. */
public final class TargetDummySetupFix {
    private TargetDummySetupFix() { }

    public static boolean enabled() {
        return readEnabled(FMLPaths.CONFIGDIR.get().resolve("vhaccelerator-common.toml"));
    }

    static boolean readEnabled(Path path) {
        if (!Files.isRegularFile(path)) {
            return true;
        }
        try (CommentedFileConfig config = CommentedFileConfig.of(path)) {
            config.load();
            Object value = config.get(List.of("compatibility", "deferTargetDummyDispenserRegistration"));
            return !(value instanceof Boolean enabled) || enabled;
        } catch (RuntimeException failure) {
            LogManager.getLogger("VH Accelerator").warn(
                    "Could not read Target Dummy startup correction setting; retaining safe default", failure);
            return true;
        }
    }

    /**
     * The caller supplies FMLCommonSetupEvent::enqueueWork, NOT a worker executor.
     * Do not fall back to an immediate write on failure: that recreates the race.
     * Forge retains ownership of the deferred task and its exception reporting.
     */
    public static void defer(Consumer<Runnable> enqueueWork, Runnable registration) {
        enqueueWork.accept(registration);
    }
}
