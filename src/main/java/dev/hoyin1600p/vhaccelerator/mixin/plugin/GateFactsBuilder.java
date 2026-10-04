package dev.hoyin1600p.vhaccelerator.mixin.plugin;

/** Mutable collector that {@link #build()} freezes into a {@link GateFacts}. */
final class GateFactsBuilder {
    boolean modernFixLoaded;
    boolean modDiscoveryFailed;
    boolean modListKnown;
    boolean ferriteCoreLoaded;
    boolean externalShapeOptimizerLoaded;
    boolean optifinePresent;
    boolean jeiLoaded;
    int jeiGeneration;
    boolean vaultHuntersLoaded;
    boolean powahLoaded;
    boolean jeiTweakerLoaded;
    boolean jerLoaded;
    boolean craftTweakerLoaded;
    boolean thermalLoaded;
    boolean ironFurnacesLoaded;
    boolean industrialForegoingLoaded;
    boolean ae2Loaded;
    boolean elevatorLoaded;
    boolean extraStorageLoaded;
    boolean refinedStorageLoaded;
    boolean sophisticatedCoreLoaded;
    boolean supplementariesLoaded;
    boolean ctmCompatible;
    boolean ctmInstalled;
    boolean buildScapeInstalled;
    boolean mekanismModelBakeCompatible;
    boolean cableTiersModelBakeCompatible;
    boolean cloudStorageModelBakeCompatible;
    boolean megaCellsModelBakeCompatible;
    boolean everyCompatDebugDumpCompatible;
    boolean decocraftCompatible;
    boolean ferriteCorePropertyMaps;
    boolean renderOptimizationLoaded;
    boolean releaseLevelPinningReferences;
    boolean copycatsLoaded;
    boolean parallelKubeJsFilters;
    boolean asyncChunkDiskReads;
    boolean create051i;
    boolean geckoLib3057;
    boolean arsNouveauGeckoLib;
    boolean everyCompatPackCache;
    boolean vaultCascadeScan;
    boolean xaeroMinimapCompatible;
    boolean xaeroWorldMapCompatible;
    boolean farsightLoaded;
    boolean vroOwnsFarsightBound;
    boolean physicalClient;
    boolean targetDummySetupFix;
    boolean smoothBootPriorityRestore;
    boolean skipReturningPlayerSpawnSearch;

    GateFacts build() {
        return new GateFacts(this);
    }
}
