package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCompareMode;
import dev.hoyin1600p.vhaccelerator.bootstrap.ConfigMigration;
import java.nio.file.Path;
import net.minecraft.CrashReport;
import net.minecraft.util.MemoryReserve;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Moves the report-building half of {@code CrashReport.preload()} off the
 * launch's critical path.
 *
 * <p>{@code Main.main} calls {@code CrashReport.preload()} on the main thread
 * before starting bootstrap. It reserves emergency memory and then builds and
 * discards a full crash report only so the crash-reporting classes are
 * loaded before memory could run out. In the Remastered test pack that report cost
 * ~0.8 s (Patchouli's crash-report hook ~0.4 s and OSHI hardware queries
 * ~0.4 s) while nothing else could start. The memory reservation stays
 * synchronous. The throwaway report is built on a daemon thread started
 * right after {@code Bootstrap.validate()}, so it overlaps the Minecraft
 * constructor and mod loading. It is not started during bootstrap, so
 * crash-report hooks never initialise classes against half-built vanilla
 * registries.</p>
 */
public final class AsyncCrashReportPreload {
    private static boolean pending;

    private AsyncCrashReportPreload() {
    }

    /** Replaces the {@code CrashReport.preload()} call in {@code Main.main}. */
    public static void preload() {
        if (!enabled()) {
            CrashReport.preload();
            return;
        }
        MemoryReserve.allocate();
        pending = true;
    }

    /** Called on the main thread after {@code Bootstrap.validate()}. */
    public static void startAfterBootstrap() {
        if (!pending) {
            return;
        }
        pending = false;
        Thread preloader = new Thread(
                AsyncCrashReportPreload::buildThrowawayReport,
                "VH Accelerator crash-report preload"
        );
        preloader.setDaemon(true);
        preloader.start();
    }

    private static void buildThrowawayReport() {
        try {
            new CrashReport("Don't panic!", new Throwable()).getFriendlyReport();
        } catch (Throwable failure) {
            // Only class preloading is lost; a real crash still reports normally.
            VHAccelerator.LOGGER.debug("Background crash-report preload failed", failure);
        }
    }

    private static boolean enabled() {
        try {
            if (BootstrapCompareMode.enabled()) {
                return false;
            }
            Path config = FMLPaths.CONFIGDIR.get().resolve(ConfigMigration.CLIENT_CONFIG);
            return EarlyBooleanOption.read(
                    config,
                    VHAcceleratorClientConfig.VALUES.enableClientOptimizations.getPath(),
                    true
            ) && EarlyBooleanOption.read(
                    config,
                    VHAcceleratorClientConfig.VALUES.asyncCrashReportPreload.getPath(),
                    true
            );
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }
}
