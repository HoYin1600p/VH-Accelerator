package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** The startup log lines that say which compatibility paths mixin selection detected. */
public final class StartupReport {
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");

    private StartupReport() {
    }

    public static void logDetections(GateFacts facts) {
        if (facts.modernFixLoaded) {
            LOGGER.info("ModernFix detected; disabling overlapping VH Accelerator mixins");
        }
        if (facts.smoothBootPriorityRestore) {
            LOGGER.info(
                    "Smooth Boot detected; VH Accelerator will restore normal priority for the "
                            + "Bootstrap, Main, IO and modloading-worker threads it lowers"
            );
        }
        if (facts.jeiLoaded && facts.jeiGeneration == 0) {
            LOGGER.warn(
                    "JEI was detected, but its internal generation is unsupported; "
                            + "JEI compatibility mixins will stay disabled"
            );
        } else if (facts.jeiGeneration != 0) {
            LOGGER.info("Detected JEI {} compatibility generation", facts.jeiGeneration);
        }
        if (facts.xaeroMinimapCompatible || facts.xaeroWorldMapCompatible) {
            LOGGER.info(
                    "Validated Xaero startup compatibility: minimap={}, worldmap={}",
                    facts.xaeroMinimapCompatible,
                    facts.xaeroWorldMapCompatible
            );
        }
        if (facts.ctmCompatible) {
            LOGGER.info(
                    "Validated ConnectedTexturesMod model-bake "
                            + "compatibility"
            );
        }
        if (facts.mekanismModelBakeCompatible
                || facts.cableTiersModelBakeCompatible
                || facts.cloudStorageModelBakeCompatible
                || facts.megaCellsModelBakeCompatible) {
            LOGGER.info(
                    "Validated additional model-bake indexes: mekanism={}, "
                            + "cabletiers={}, cloudstorage={}, megacells={}",
                    facts.mekanismModelBakeCompatible,
                    facts.cableTiersModelBakeCompatible,
                    facts.cloudStorageModelBakeCompatible,
                    facts.megaCellsModelBakeCompatible
            );
        }
        if (facts.everyCompatDebugDumpCompatible) {
            LOGGER.info(
                    "Validated EveryCompat generated-resource compatibility"
            );
        }
        if (facts.farsightLoaded) {
            LOGGER.info(
                    facts.vroOwnsFarsightBound
                            ? "Farsight detected; leaving its chunk retention "
                                    + "bound to Vault Render Optimization"
                            : "Farsight detected; VH Accelerator will bound "
                                    + "its retained client chunks"
            );
        }
    }
}
