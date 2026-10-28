package dev.hoyin1600p.vhaccelerator.config;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Common-side setting metadata shared by local controls and the operator server editor. */
public final class ServerSettingCatalog {
    public record Entry(String id, String category, List<String> path, boolean restartRequired) {
        public Entry { path = List.copyOf(path); }
        public String key() { return String.join(".", path); }
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("enableCommonOptimizations", "general", List.of("optimizations", "enableCommonOptimizations"), true),
            new Entry("optimizeVoxelShapeMerging", "launch", List.of("optimizations", "optimizeVoxelShapeMerging"), true),
            new Entry("cacheLaunchVoxelShapes", "launch", List.of("optimizations", "cacheLaunchVoxelShapes"), true),
            new Entry("cacheResourceListing", "launch", List.of("optimizations", "cacheResourceListing"), false),
            new Entry("fixBuddingCrystalChunkLoad", "vault", List.of("compatibility", "fixBuddingCrystalChunkLoad"), false),
            new Entry("optimizeVaultLootCdf", "vault", List.of("compatibility", "optimizeVaultLootCdf"), false),
            new Entry("lazyCompactVaultLootCdf", "vault", List.of("compatibility", "lazyCompactVaultLootCdf"), false),
            new Entry("deferTargetDummyDispenserRegistration", "compat", List.of("compatibility", "deferTargetDummyDispenserRegistration"), true),
            new Entry("restoreSmoothBootThreadPriorities", "compat", List.of("compatibility", "restoreSmoothBootThreadPriorities"), true),
            new Entry("parallelKubeJsRecipeFilters", "compat", List.of("optimizations", "parallelKubeJsRecipeFilters"), true),
            new Entry("indexKubeJsRecipeRemovals", "compat", List.of("optimizations", "indexKubeJsRecipeRemovals"), true),
            new Entry("trimDecocraftModels", "memory", List.of("compatibility", "trimDecocraftModels"), true),
            new Entry("compactFerriteCorePropertyMaps", "memory", List.of("compatibility", "compactFerriteCorePropertyMaps"), true),
            new Entry("compactBuildingGadgetsTemplates", "memory", List.of("compatibility", "compactBuildingGadgetsTemplates"), true),
            new Entry("shareAe2ThingsDiskStrings", "memory", List.of("compatibility", "shareAe2ThingsDiskStrings"), true),
            new Entry("compactWorldZonesMemberships", "memory", List.of("compatibility", "compactWorldZonesMemberships"), true),
            new Entry("releaseLevelPinningReferences", "memory", List.of("compatibility", "releaseLevelPinningReferences"), true),
            new Entry("releaseMixinClassTrees", "memory", List.of("optimizations", "releaseMixinClassTrees"), true),
            new Entry("streamlineObjectHolderCleanup", "server", List.of("optimizations", "streamlineObjectHolderCleanup"), true),
            new Entry("parallelReloadPreparation", "server", List.of("optimizations", "parallelReloadPreparation"), false),
            new Entry("skipRedundantRegistryValidation", "server", List.of("optimizations", "skipRedundantRegistryValidation"), true),
            new Entry("skipRegistryDump", "server", List.of("optimizations", "skipRegistryDump"), true),
            new Entry("parallelBlockStateInit", "server", List.of("optimizations", "parallelBlockStateInit"), true),
            new Entry("lazyBlockStateCache", "server", List.of("optimizations", "lazyBlockStateCache"), true),
            new Entry("indexImmutableModResources", "server", List.of("optimizations", "indexImmutableModResources"), true),
            new Entry("indexCraftTweakerRecipeRemovals", "server", List.of("optimizations", "indexCraftTweakerRecipeRemovals"), true),
            new Entry("shortCircuitLootJsRegexFilters", "compat", List.of("compatibility", "shortCircuitLootJsRegexFilters"), true),
            new Entry("deferThermalConvertedFuels", "compat", List.of("compatibility", "deferThermalConvertedFuels"), true),
            new Entry("asyncChunkDiskReads", "server", List.of("optimizations", "asyncChunkDiskReads"), true),
            new Entry("indexObjectHolderDispatch", "server", List.of("optimizations", "indexObjectHolderDispatch"), true),
            new Entry("lazyBlockStatePropertyCodecs", "server", List.of("optimizations", "lazyBlockStatePropertyCodecs"), true),
            new Entry("presizeBlockStateIdMap", "server", List.of("optimizations", "presizeBlockStateIdMap"), true),
            new Entry("skipReturningPlayerSpawnSearch", "server", List.of("optimizations", "skipReturningPlayerSpawnSearch"), true),
            new Entry("forgeHandshakeBatching", "backports", List.of("backports", "forgeHandshakeBatching"), true),
            new Entry("attributeSupplierDeduplication", "backports", List.of("backports", "attributeSupplierDeduplication"), true),
            new Entry("attachCapabilitiesDispatch", "backports", List.of("backports", "attachCapabilitiesDispatch"), true),
            new Entry("compactModFileScanData", "backports", List.of("backports", "compactModFileScanData"), true),
            new Entry("forgeTagConcurrencyFixes", "backports", List.of("backports", "forgeTagConcurrencyFixes"), true),
            new Entry("serverEventLoopFix", "backports", List.of("backports", "serverEventLoopFix"), true),
            new Entry("overrideModernFixDynamicResources", "backports", List.of("backports", "overrideModernFixDynamicResources"), true),
            new Entry("responsiveModWorkQueue", "backports", List.of("backports", "responsiveModWorkQueue"), true),
            new Entry("modernFixIntegratedWatchdogCorrection", "backports", List.of("backports", "modernFixIntegratedWatchdogCorrection"), true),
            new Entry("stateDefinitionConstruction", "backports", List.of("backports", "stateDefinitionConstruction"), true),
            new Entry("compactPaletteValidation", "backports", List.of("backports", "compactPaletteValidation"), true),
            new Entry("modernFixJeiSearchSnapshot", "backports", List.of("backports", "modernFixJeiSearchSnapshot"), true),
            new Entry("modernFixNightConfigWatcherCorrection", "backports", List.of("backports", "modernFixNightConfigWatcherCorrection"), true),
            new Entry("backgroundWorkerLimit", "backports", List.of("backports", "backgroundWorkerLimit"), true),
            new Entry("compactEntityModels", "backports", List.of("backports", "compactEntityModels"), true),
            new Entry("worldgenMaterialRuleIteration", "backports", List.of("backports", "worldgenMaterialRuleIteration"), true),
            new Entry("worldgenSurfaceRuleIteration", "backports", List.of("backports", "worldgenSurfaceRuleIteration"), true),
            new Entry("worldgenNoiseFunctionCache", "backports", List.of("backports", "worldgenNoiseFunctionCache"), true),
            new Entry("worldgenBiomeSupplierReuse", "backports", List.of("backports", "worldgenBiomeSupplierReuse"), true),
            new Entry("worldgenDirectYConditions", "backports", List.of("backports", "worldgenDirectYConditions"), true),
            new Entry("worldgenDeferredClimateTree", "backports", List.of("backports", "worldgenDeferredClimateTree"), true),
            new Entry("earlyStructureLocationRejection", "backports", List.of("backports", "earlyStructureLocationRejection"), true),
            new Entry("compactObjectHolderThrowables", "backports", List.of("backports", "compactObjectHolderThrowables"), true),
            new Entry("removeRedundantObjectHolderCallbacks", "backports", List.of("backports", "removeRedundantObjectHolderCallbacks"), true),
            new Entry("fasterLootLoading", "backports", List.of("backports", "fasterLootLoading"), true),
            new Entry("dynamicClientLanguages", "backports", List.of("backports", "dynamicClientLanguages"), true),
            new Entry("disableTelemetry", "backports", List.of("backports", "disableTelemetry"), true),
            new Entry("fasterIngredientTagLookups", "backports", List.of("backports", "fasterIngredientTagLookups"), true),
            new Entry("fasterIngredientExpansionCache", "backports", List.of("backports", "fasterIngredientExpansionCache"), true),
            new Entry("ingredientItemValueDeduplication", "backports", List.of("backports", "ingredientItemValueDeduplication"), true),
            new Entry("mappedRegistryGrowth", "backports", List.of("backports", "mappedRegistryGrowth"), true),
            new Entry("forgeRegistryLambdaElision", "backports", List.of("backports", "forgeRegistryLambdaElision"), true),
            new Entry("forgeRegistryRegistrationAcceleration", "backports", List.of("backports", "forgeRegistryRegistrationAcceleration"), true),
            new Entry("deduplicateResourceLocationNamespaces", "backports", List.of("backports", "deduplicateResourceLocationNamespaces"), true),
            new Entry("blockPropertyNameDeduplication", "backports", List.of("backports", "blockPropertyNameDeduplication"), true),
            new Entry("deduplicateWallShapes", "backports", List.of("backports", "deduplicateWallShapes"), true),
            new Entry("potentialSpawnCopyOnWrite", "backports", List.of("backports", "potentialSpawnCopyOnWrite"), true),
            new Entry("reduceTickingChunkAllocations", "backports", List.of("backports", "reduceTickingChunkAllocations"), true),
            new Entry("resourceKeyInterning", "backports", List.of("backports", "resourceKeyInterning"), true),
            new Entry("fasterIngredientEmptinessCheck", "backports", List.of("backports", "fasterIngredientEmptinessCheck"), true),
            new Entry("debugLevelSourceStateView", "backports", List.of("backports", "debugLevelSourceStateView"), true),
            new Entry("fastRegistryFreezeCheck", "backports", List.of("backports", "fastRegistryFreezeCheck"), true),
            new Entry("strongholdRingEarlyRejection", "backports", List.of("backports", "strongholdRingEarlyRejection"), true),
            new Entry("reloadBarrierRaceFix", "backports", List.of("backports", "reloadBarrierRaceFix"), true),
            new Entry("registryWriteConcurrencyFix", "backports", List.of("backports", "registryWriteConcurrencyFix"), true),
            new Entry("eventBusWrapperCacheConcurrencyFix", "backports", List.of("backports", "eventBusWrapperCacheConcurrencyFix"), true),
            new Entry("compareMode", "diagnostics", List.of("diagnostics", "compareMode"), true),
            new Entry("timers", "diagnostics", List.of("diagnostics", "timers"), false),
            new Entry("debug", "diagnostics", List.of("diagnostics", "debug"), true),
            new Entry("jeiRecipeAudit", "diagnostics", List.of("diagnostics", "jeiRecipeAudit"), false),
            new Entry("startupJfr", "diagnostics", List.of("diagnostics", "startupJfr"), true)
    );
    private static final Map<String, Entry> BY_KEY = index();

    private ServerSettingCatalog() { }

    public static List<Entry> all() { return ENTRIES; }
    public static Entry find(String key) { return BY_KEY.get(key); }

    private static Map<String, Entry> index() {
        Map<String, Entry> values = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            if (values.put(entry.key(), entry) != null) throw new IllegalStateException("Duplicate server setting");
        }
        return java.util.Collections.unmodifiableMap(values);
    }
}
