package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCommonConfig;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCompareMode;
import dev.hoyin1600p.vhaccelerator.compat.farsight.FarsightBoundOwner;
import dev.hoyin1600p.vhaccelerator.compat.ferritecore.FerriteCorePropertyMaps;
import dev.hoyin1600p.vhaccelerator.compat.smoothboot.SmoothBootThreadPriorities;
import dev.hoyin1600p.vhaccelerator.compat.targetdummy.TargetDummySetupFix;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.LoadingModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Reads the loaded-mod list and launch configuration into {@link GateFacts} during mixin selection. */
public final class GateFactsDiscovery {
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");

    private GateFactsDiscovery() {
    }

    /** Never throws: a failed mod-list query is recorded in the facts instead. */
    public static GateFacts discover() {
        GateFactsBuilder b = new GateFactsBuilder();
        b.skipReturningPlayerSpawnSearch = !BootstrapCompareMode.enabled()
                && BootstrapCommonConfig.bool(
                        "optimizations", "enableCommonOptimizations", true)
                && BootstrapCommonConfig.bool(
                        "optimizations", "skipReturningPlayerSpawnSearch", true);
        try {
            Class.forName(
                    "optifine.OptiFineTransformationService",
                    false,
                    GateFactsDiscovery.class.getClassLoader()
            );
            b.optifinePresent = true;
        } catch (ClassNotFoundException ignored) {
            b.optifinePresent = false;
        } catch (RuntimeException | LinkageError failure) {
            b.optifinePresent = true;
            LOGGER.debug(
                    "Could not safely exclude OptiFine from vanilla resource indexing",
                    failure
            );
        }
        b.physicalClient = FMLEnvironment.dist == Dist.CLIENT;
        try {
            LoadingModList modList = LoadingModList.get();
            b.modListKnown = modList != null;
            b.targetDummySetupFix = ModPresence.hasVersion(modList, "dummmmmmy", "1.18-1.5.2")
                    && TargetDummySetupFix.enabled();
            b.modernFixLoaded = modList != null && modList.getModFileById("modernfix") != null;
            b.ctmInstalled = modList != null && modList.getModFileById("ctm") != null;
            b.buildScapeInstalled = modList != null
                    && modList.getModFileById("buildscape") != null;
            b.smoothBootPriorityRestore = modList != null
                    && modList.getModFileById("smoothboot") != null
                    && !BootstrapCompareMode.enabled()
                    && SmoothBootThreadPriorities.enabled();
            b.ferriteCoreLoaded = modList != null
                    && modList.getModFileById("ferritecore") != null;
            b.externalShapeOptimizerLoaded = modList != null
                    && (modList.getModFileById("canary") != null
                    || modList.getModFileById("lithium") != null);
            // Both sides: Decocraft parses its Blockbench models at block registration.
            b.decocraftCompatible = ModPresence.hasVersion(
                    modList,
                    "decocraft",
                    "3.0.4-1.18.2"
            );
            b.copycatsLoaded = modList != null && modList.getModFileById("copycats") != null;
            b.create051i = ModPresence.hasVersion(modList, "create", "0.5.1.i");
            b.geckoLib3057 = ModPresence.hasVersion(modList, "geckolib3", "3.0.57");
            b.arsNouveauGeckoLib = ModPresence.hasVersion(modList, "ars_nouveau", "2.9.0");
            b.everyCompatPackCache = (ModPresence.hasVersion(modList, "selene", "1.18.2-1.17.14")
                    || ModPresence.hasVersion(modList, "selene", "1.18.2-1.17.17"))
                    && (ModPresence.hasVersion(modList, "everycomp", "1.18.2-1.5.18")
                    || ModPresence.hasVersion(modList, "everycomp", "1.18.2-1.6.7"));
            b.parallelKubeJsFilters = ModPresence.hasVersion(modList, "kubejs", "1802.5.5-build.569")
                    && !BootstrapCompareMode.enabled()
                    && BootstrapCommonConfig.bool(
                            "optimizations", "parallelKubeJsRecipeFilters", true);
            b.asyncChunkDiskReads = !BootstrapCompareMode.enabled()
                    && BootstrapCommonConfig.bool(
                            "optimizations", "asyncChunkDiskReads", true);
            b.releaseLevelPinningReferences = BootstrapCommonConfig.bool(
                    "compatibility", "releaseLevelPinningReferences", true);
            b.renderOptimizationLoaded = modList != null
                    && modList.getModFileById("vault_render_optimization") != null;
            b.ferriteCorePropertyMaps = ModPresence.hasVersion(modList, "ferritecore", "4.2.2")
                    && !BootstrapCompareMode.enabled()
                    && FerriteCorePropertyMaps.enabled();
            if (b.physicalClient) {
                b.jeiLoaded = modList != null && modList.getModFileById("jei") != null;
                if (b.jeiLoaded
                        && modList.findResource(
                                "mezz/jei/common/startup/JeiStarter.class"
                        ) != null) {
                    b.jeiGeneration = 10;
                } else if (b.jeiLoaded
                        && modList.findResource(
                                "mezz/jei/startup/JeiStarter.class"
                        ) != null) {
                    b.jeiGeneration = 9;
                }
                b.vaultHuntersLoaded =
                        modList != null && modList.getModFileById("the_vault") != null;
                b.vaultCascadeScan = b.vaultHuntersLoaded && ModPresence.vaultStillScansCascadeStacks(modList);
                b.powahLoaded = modList != null && modList.getModFileById("powah") != null;
                b.jeiTweakerLoaded =
                        modList != null && modList.getModFileById("jeitweaker") != null;
                b.jerLoaded = modList != null
                        && modList.getModFileById("jeresources") != null;
                b.craftTweakerLoaded = modList != null
                        && modList.getModFileById("crafttweaker") != null;
                b.thermalLoaded = modList != null
                        && modList.getModFileById("thermal") != null;
                b.ironFurnacesLoaded = modList != null
                        && modList.getModFileById("ironfurnaces") != null;
                b.industrialForegoingLoaded = modList != null
                        && modList.getModFileById("industrialforegoing") != null;
                b.ae2Loaded = modList != null
                        && modList.getModFileById("ae2") != null;
                b.elevatorLoaded = modList != null
                        && modList.getModFileById("elevatorid") != null;
                b.extraStorageLoaded = modList != null
                        && modList.getModFileById("extrastorage") != null;
                b.refinedStorageLoaded = modList != null
                        && modList.getModFileById("refinedstorage") != null;
                b.sophisticatedCoreLoaded = modList != null
                        && modList.getModFileById("sophisticatedcore") != null;
                b.supplementariesLoaded = modList != null
                        && modList.getModFileById("supplementaries") != null;
                b.ctmCompatible = ModPresence.hasVersion(
                        modList,
                        "ctm",
                        "1.18.2-1.1.5+5"
                );
                b.mekanismModelBakeCompatible = ModPresence.hasVersion(
                        modList,
                        "mekanism",
                        "10.2.5"
                );
                b.cableTiersModelBakeCompatible = ModPresence.hasVersion(
                        modList,
                        "cabletiers",
                        "1.18.2-0.56"
                );
                b.cloudStorageModelBakeCompatible = ModPresence.hasVersion(
                        modList,
                        "cloudstorage",
                        "1.1.0"
                );
                b.megaCellsModelBakeCompatible = ModPresence.hasVersion(
                        modList,
                        "megacells",
                        "1.4.2-1.18.2"
                );
                b.everyCompatDebugDumpCompatible = ModPresence.hasVersion(
                        modList,
                        "everycomp",
                        "1.18.2-1.6.7"
                ) && ModPresence.hasVersion(
                        modList,
                        "selene",
                        "1.18.2-1.17.14"
                );
                b.xaeroMinimapCompatible = ModPresence.hasVersion(
                        modList,
                        "xaerominimap",
                        "25.2.10"
                );
                b.xaeroWorldMapCompatible = ModPresence.hasVersion(
                        modList,
                        "xaeroworldmap",
                        "1.39.12"
                );
                b.farsightLoaded = FarsightBoundOwner.farsightLoaded(modList);
                b.vroOwnsFarsightBound = FarsightBoundOwner.vroOwnsBound(modList);
            }
        } catch (RuntimeException exception) {
            b.modDiscoveryFailed = true;
            b.modListKnown = false;
            b.targetDummySetupFix = false;
            b.smoothBootPriorityRestore = false;
            b.modernFixLoaded = false;
            b.ferriteCoreLoaded = false;
            b.externalShapeOptimizerLoaded = false;
            b.jeiLoaded = false;
            b.jeiGeneration = 0;
            b.vaultHuntersLoaded = false;
            b.powahLoaded = false;
            b.jeiTweakerLoaded = false;
            b.jerLoaded = false;
            b.craftTweakerLoaded = false;
            b.thermalLoaded = false;
            b.ironFurnacesLoaded = false;
            b.industrialForegoingLoaded = false;
            b.ae2Loaded = false;
            b.elevatorLoaded = false;
            b.extraStorageLoaded = false;
            b.refinedStorageLoaded = false;
            b.sophisticatedCoreLoaded = false;
            b.supplementariesLoaded = false;
            b.ctmCompatible = false;
            b.ctmInstalled = false;
            b.mekanismModelBakeCompatible = false;
            b.cableTiersModelBakeCompatible = false;
            b.cloudStorageModelBakeCompatible = false;
            b.megaCellsModelBakeCompatible = false;
            b.everyCompatDebugDumpCompatible = false;
            b.decocraftCompatible = false;
            b.ferriteCorePropertyMaps = false;
            b.renderOptimizationLoaded = true;
            b.xaeroMinimapCompatible = false;
            b.xaeroWorldMapCompatible = false;
            b.farsightLoaded = false;
            LOGGER.debug("Loaded mods could not be queried during mixin selection", exception);
        }
        return b.build();
    }
}
