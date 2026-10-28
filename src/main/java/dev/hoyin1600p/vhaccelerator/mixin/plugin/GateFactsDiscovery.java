package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCommonConfig;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCompareMode;
import dev.hoyin1600p.vhaccelerator.shape.ShapeLaunchPolicy;
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
        b.commonMasterEnabled = !BootstrapCompareMode.enabled()
                && BootstrapCommonConfig.bool("optimizations", "enableCommonOptimizations", true);
        b.indexCraftTweakerRecipeRemovals = BootstrapCommonConfig.bool(
                "optimizations", "indexCraftTweakerRecipeRemovals", true);
        b.decocraftTrimmingEnabled = BootstrapCommonConfig.bool("compatibility", "trimDecocraftModels", true);
        // Dedicated servers only; a client decides at run time from its own launch snapshot.
        b.launchShapeCache = ShapeLaunchPolicy.serverSelected(ShapeLaunchPolicy.Feature.CACHE);
        b.flatShapeMerger = ShapeLaunchPolicy.serverSelected(ShapeLaunchPolicy.Feature.MERGER);
        b.compactBuildingGadgetsTemplates = BootstrapCommonConfig.bool(
                "compatibility", "compactBuildingGadgetsTemplates", true);
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
            // Budding crystal correctness hooks run on both physical sides.
            b.vaultHuntersLoaded = modList != null && modList.getModFileById("the_vault") != null;
            b.vhapiLoaded = modList != null && modList.getModFileById("vhapi") != null;
            b.vaultCdfCompatible = !b.vhapiLoaded && ModPresence.vaultCdfCompatible(modList);
            b.vaultCdfLazyCompatible = ModPresence.vaultCdfLazyCompatible(modList);
            b.worldZonesCompaction = !b.physicalClient && b.commonMasterEnabled && !BootstrapCompareMode.enabled()
                    && BootstrapCommonConfig.bool("compatibility", "compactWorldZonesMemberships", true)
                    && ModPresence.worldZonesCompatible(modList);
            b.targetDummySetupFix = ModPresence.hasVersion(modList, "dummmmmmy", "1.18-1.5.2")
                    && TargetDummySetupFix.enabled();
            b.modernFixLoaded = modList != null && modList.getModFileById("modernfix") != null;
            b.ctmInstalled = modList != null && modList.getModFileById("ctm") != null;
            b.buildScapeInstalled = modList != null
                    && modList.getModFileById("buildscape") != null;
            b.buildScapeReloadBarrierFix = b.buildScapeInstalled
                    && ModPresence.hasResource(modList, "buildscape",
                    "com/kingodogo/buildscape/mixin/SimpleReloadInstanceMixin.class");
            b.smoothBootPriorityRestore = modList != null
                    && modList.getModFileById("smoothboot") != null
                    && !BootstrapCompareMode.enabled()
                    && SmoothBootThreadPriorities.enabled();
            b.ferriteCoreLoaded = modList != null
                    && modList.getModFileById("ferritecore") != null;
            b.canaryLoaded = modList != null && modList.getModFileById("canary") != null;
            b.lithiumLoaded = modList != null && modList.getModFileById("lithium") != null;
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
            b.indexKubeJsRecipeRemovals = ModPresence.hasVersion(modList, "kubejs", "1802.5.5-build.569")
                    && !BootstrapCompareMode.enabled()
                    && BootstrapCommonConfig.bool(
                            "optimizations", "indexKubeJsRecipeRemovals", true);
            b.asyncChunkDiskReads = !BootstrapCompareMode.enabled()
                    && BootstrapCommonConfig.bool(
                            "optimizations", "asyncChunkDiskReads", true);
            b.indexObjectHolderDispatch = b.commonMasterEnabled && !BootstrapCompareMode.enabled()
                    && BootstrapCommonConfig.bool("optimizations", "indexObjectHolderDispatch", true);
            // Dedicated servers only; the shape of StateDefinition itself is checked by the compat group preflight.
            b.lazyStatePropertyCodecs = !b.physicalClient && b.commonMasterEnabled && !BootstrapCompareMode.enabled()
                    && BootstrapCommonConfig.bool("optimizations", "lazyBlockStatePropertyCodecs", true);
            // Both sides: Forge fills the block-state id map the same way on a client and a server. The map and
            // Forge layouts are checked at run time and any mismatch keeps the original fill.
            b.presizeBlockStateIdMap = b.commonMasterEnabled && !BootstrapCompareMode.enabled()
                    && BootstrapCommonConfig.bool("optimizations", "presizeBlockStateIdMap", true);
            b.releaseLevelPinningReferences = BootstrapCommonConfig.bool(
                    "compatibility", "releaseLevelPinningReferences", true);
            b.renderOptimizationLoaded = modList != null
                    && modList.getModFileById("vault_render_optimization") != null;
            b.ferriteCorePropertyMaps = ModPresence.hasVersion(modList, "ferritecore", "4.2.2")
                    && !BootstrapCompareMode.enabled()
                    && FerriteCorePropertyMaps.enabled();
            // Exact common-safe removal hooks also run on dedicated servers.
            b.craftTweakerRemovalsCompatible = ModPresence.hasVersion(
                    modList, "crafttweaker", "9.1.213");
            // Both sides: a singleplayer world loads AE2Things disks too. Presence only; the exact
            // class layout is checked by the compatibility group's preflight.
            b.ae2ThingsStringSharing = b.commonMasterEnabled
                    && modList != null && modList.getModFileById("ae2things") != null
                    && BootstrapCommonConfig.bool("compatibility", "shareAe2ThingsDiskStrings", true);
            // Both sides: the integrated server runs loot modifiers too. Presence only (LootJS applies its
            // filters through KubeJS, so both must be installed); the exact class layout is checked by the
            // compatibility group's preflight.
            b.lootJsRegexShortCircuit = b.commonMasterEnabled && !BootstrapCompareMode.enabled()
                    && modList != null && modList.getModFileById("lootjs") != null
                    && modList.getModFileById("kubejs") != null
                    && BootstrapCommonConfig.bool("compatibility", "shortCircuitLootJsRegexFilters", true);
            // Dedicated servers only: the converted-fuel lists exist for Thermal's JEI plugin, a client mod.
            // The exact class layout is checked by the compatibility group's preflight.
            b.thermalDeferredFuelLists = !b.physicalClient && b.commonMasterEnabled && !BootstrapCompareMode.enabled()
                    && modList != null && modList.getModFileById("thermal") != null
                    && BootstrapCommonConfig.bool("compatibility", "deferThermalConvertedFuels", true);
            b.buildingGadgetsTemplatesCompatible = !b.physicalClient && b.commonMasterEnabled
                    && b.compactBuildingGadgetsTemplates && ModPresence.buildingGadgetsTemplatesCompatible(modList);
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
            b.buildScapeReloadBarrierFix = false;
            b.ferriteCoreLoaded = false;
            b.canaryLoaded = false;
            b.lithiumLoaded = false;
            b.launchShapeCache = false;
            b.flatShapeMerger = false;
            b.jeiLoaded = false;
            b.jeiGeneration = 0;
            b.vaultHuntersLoaded = false;
            b.vhapiLoaded = false;
            b.vaultCdfCompatible = false;
            b.vaultCdfLazyCompatible = false;
            b.powahLoaded = false;
            b.jeiTweakerLoaded = false;
            b.jerLoaded = false;
            b.craftTweakerLoaded = false;
            b.craftTweakerRemovalsCompatible = false;
            b.buildingGadgetsTemplatesCompatible = false;
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
            b.ae2ThingsStringSharing = false;
            b.lootJsRegexShortCircuit = false;
            b.thermalDeferredFuelLists = false;
            b.indexObjectHolderDispatch = false;
            b.lazyStatePropertyCodecs = false;
            b.presizeBlockStateIdMap = false;
            b.renderOptimizationLoaded = true;
            b.xaeroMinimapCompatible = false;
            b.xaeroWorldMapCompatible = false;
            b.farsightLoaded = false;
            LOGGER.debug("Loaded mods could not be queried during mixin selection", exception);
        }
        return b.build();
    }
}
