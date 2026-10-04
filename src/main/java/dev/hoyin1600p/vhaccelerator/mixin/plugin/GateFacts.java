package dev.hoyin1600p.vhaccelerator.mixin.plugin;

/**
 * What the mixin plugin learned about the running game while it loaded:
 * which side it is, which mods are present, and which versions are the exact
 * ones VH Accelerator's compatibility mixins were written for. Immutable once
 * built; {@link GateFactsDiscovery} fills a {@link GateFactsBuilder} during {@code onLoad}.
 */
public final class GateFacts {
    public final boolean modernFixLoaded;
    final boolean modDiscoveryFailed;
    final boolean modListKnown;
    public final boolean ferriteCoreLoaded;
    final boolean externalShapeOptimizerLoaded;
    final boolean optifinePresent;
    final boolean jeiLoaded;
    final int jeiGeneration;
    final boolean vaultHuntersLoaded;
    final boolean powahLoaded;
    final boolean jeiTweakerLoaded;
    final boolean jerLoaded;
    final boolean craftTweakerLoaded;
    final boolean thermalLoaded;
    final boolean ironFurnacesLoaded;
    final boolean industrialForegoingLoaded;
    final boolean ae2Loaded;
    final boolean elevatorLoaded;
    final boolean extraStorageLoaded;
    final boolean refinedStorageLoaded;
    final boolean sophisticatedCoreLoaded;
    final boolean supplementariesLoaded;
    final boolean ctmCompatible;
    final boolean ctmInstalled;
    final boolean buildScapeInstalled;
    final boolean mekanismModelBakeCompatible;
    final boolean cableTiersModelBakeCompatible;
    final boolean cloudStorageModelBakeCompatible;
    final boolean megaCellsModelBakeCompatible;
    final boolean everyCompatDebugDumpCompatible;
    final boolean decocraftCompatible;
    final boolean ferriteCorePropertyMaps;
    final boolean renderOptimizationLoaded;
    final boolean releaseLevelPinningReferences;
    final boolean copycatsLoaded;
    final boolean parallelKubeJsFilters;
    final boolean asyncChunkDiskReads;
    final boolean create051i;
    final boolean geckoLib3057;
    final boolean arsNouveauGeckoLib;
    final boolean everyCompatPackCache;
    final boolean vaultCascadeScan;
    final boolean xaeroMinimapCompatible;
    final boolean xaeroWorldMapCompatible;
    final boolean farsightLoaded;
    final boolean vroOwnsFarsightBound;
    public final boolean physicalClient;
    final boolean targetDummySetupFix;
    final boolean smoothBootPriorityRestore;
    final boolean skipReturningPlayerSpawnSearch;

    GateFacts(GateFactsBuilder builder) {
        this.modernFixLoaded = builder.modernFixLoaded;
        this.modDiscoveryFailed = builder.modDiscoveryFailed;
        this.modListKnown = builder.modListKnown;
        this.ferriteCoreLoaded = builder.ferriteCoreLoaded;
        this.externalShapeOptimizerLoaded = builder.externalShapeOptimizerLoaded;
        this.optifinePresent = builder.optifinePresent;
        this.jeiLoaded = builder.jeiLoaded;
        this.jeiGeneration = builder.jeiGeneration;
        this.vaultHuntersLoaded = builder.vaultHuntersLoaded;
        this.powahLoaded = builder.powahLoaded;
        this.jeiTweakerLoaded = builder.jeiTweakerLoaded;
        this.jerLoaded = builder.jerLoaded;
        this.craftTweakerLoaded = builder.craftTweakerLoaded;
        this.thermalLoaded = builder.thermalLoaded;
        this.ironFurnacesLoaded = builder.ironFurnacesLoaded;
        this.industrialForegoingLoaded = builder.industrialForegoingLoaded;
        this.ae2Loaded = builder.ae2Loaded;
        this.elevatorLoaded = builder.elevatorLoaded;
        this.extraStorageLoaded = builder.extraStorageLoaded;
        this.refinedStorageLoaded = builder.refinedStorageLoaded;
        this.sophisticatedCoreLoaded = builder.sophisticatedCoreLoaded;
        this.supplementariesLoaded = builder.supplementariesLoaded;
        this.ctmCompatible = builder.ctmCompatible;
        this.ctmInstalled = builder.ctmInstalled;
        this.buildScapeInstalled = builder.buildScapeInstalled;
        this.mekanismModelBakeCompatible = builder.mekanismModelBakeCompatible;
        this.cableTiersModelBakeCompatible = builder.cableTiersModelBakeCompatible;
        this.cloudStorageModelBakeCompatible = builder.cloudStorageModelBakeCompatible;
        this.megaCellsModelBakeCompatible = builder.megaCellsModelBakeCompatible;
        this.everyCompatDebugDumpCompatible = builder.everyCompatDebugDumpCompatible;
        this.decocraftCompatible = builder.decocraftCompatible;
        this.ferriteCorePropertyMaps = builder.ferriteCorePropertyMaps;
        this.renderOptimizationLoaded = builder.renderOptimizationLoaded;
        this.releaseLevelPinningReferences = builder.releaseLevelPinningReferences;
        this.copycatsLoaded = builder.copycatsLoaded;
        this.parallelKubeJsFilters = builder.parallelKubeJsFilters;
        this.asyncChunkDiskReads = builder.asyncChunkDiskReads;
        this.create051i = builder.create051i;
        this.geckoLib3057 = builder.geckoLib3057;
        this.arsNouveauGeckoLib = builder.arsNouveauGeckoLib;
        this.everyCompatPackCache = builder.everyCompatPackCache;
        this.vaultCascadeScan = builder.vaultCascadeScan;
        this.xaeroMinimapCompatible = builder.xaeroMinimapCompatible;
        this.xaeroWorldMapCompatible = builder.xaeroWorldMapCompatible;
        this.farsightLoaded = builder.farsightLoaded;
        this.vroOwnsFarsightBound = builder.vroOwnsFarsightBound;
        this.physicalClient = builder.physicalClient;
        this.targetDummySetupFix = builder.targetDummySetupFix;
        this.smoothBootPriorityRestore = builder.smoothBootPriorityRestore;
        this.skipReturningPlayerSpawnSearch = builder.skipReturningPlayerSpawnSearch;
    }
}
