package dev.hoyin1600p.vhaccelerator.client.config.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hoyin1600p.vhaccelerator.client.config.catalog.ConfigSettingCatalog.Category;
import dev.hoyin1600p.vhaccelerator.client.config.catalog.ConfigSettingCatalog.Setting;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

class ConfigSettingCatalogTest {
    /** The settings Experimental turns on today, by id; a new default-off option shows up here. */
    private static final Set<String> EXPERIMENTAL_IDS = Set.of(
            "persistentVanillaRecipeValidationCache",
            "parallelReloadPreparation",
            "skipRedundantRegistryValidation",
            "skipRegistryDump",
            "parallelBlockStateInit",
            "lazyBlockStateCache"
    );

    @Test
    void everyForgeConfigValueIsCoveredExactlyOnceOrDeliberatelyUnlisted() {
        for (ConfigSettingCatalog.Storage storage : ConfigSettingCatalog.Storage.values()) {
            assertCovered(storage);
        }
    }

    private static void assertCovered(ConfigSettingCatalog.Storage storage) {
        List<String> specPaths = new ArrayList<>();
        collectValuePaths(ConfigSettingStore.spec(storage).getValues(), specPaths);
        List<String> catalogPaths = ConfigSettingCatalog.all().stream()
                .filter(setting -> setting.storage() == storage)
                .map(setting -> String.join(".", setting.path()))
                .toList();

        assertEquals(catalogPaths.size(), new HashSet<>(catalogPaths).size(), "a .toml value is listed twice");
        for (String unlisted : ConfigSettingCatalog.UNLISTED_PATHS) {
            assertTrue(specPaths.contains(unlisted), "unlisted path is not in the spec: " + unlisted);
            assertFalse(catalogPaths.contains(unlisted), "unlisted path is also listed: " + unlisted);
        }
        Set<String> expected = new HashSet<>(specPaths);
        expected.removeAll(ConfigSettingCatalog.UNLISTED_PATHS);
        assertEquals(expected, new HashSet<>(catalogPaths));
    }

    @Test
    void unlistedAndExclusionSetsAreTheReviewedOnes() {
        assertEquals(Set.of(), ConfigSettingCatalog.UNLISTED_PATHS);
        assertEquals(Set.of("parallelAtlasStitching"), ConfigSettingCatalog.EXPERIMENTAL_EXCLUSIONS);
        Set<String> ids = ConfigSettingCatalog.all().stream().map(Setting::id).collect(Collectors.toSet());
        assertTrue(ids.containsAll(ConfigSettingCatalog.EXPERIMENTAL_EXCLUSIONS), "exclusion names no setting");
    }

    @Test
    void settingIdsAndTranslationKeysAreUnique() {
        List<String> keys = ConfigSettingCatalog.all().stream().map(Setting::labelKey).toList();
        assertEquals(keys.size(), new HashSet<>(keys).size());
    }

    @Test
    void everyCatalogKeyHasEnglishText() throws Exception {
        JsonObject lang;
        try (InputStream stream = ConfigSettingCatalogTest.class.getResourceAsStream(
                "/assets/vhaccelerator/lang/en_us.json")) {
            assertNotNull(stream, "en_us.json is missing");
            lang = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<String> required = new ArrayList<>(List.of(
                "key.vhaccelerator.open_config",
                "key.categories.vhaccelerator",
                "vhaccelerator.config.title",
                "vhaccelerator.config.badge.restart",
                "vhaccelerator.config.button.default",
                "vhaccelerator.config.button.experimental",
                "vhaccelerator.config.button.report_bug",
                "vhaccelerator.config.button.report_bug.summary",
                "vhaccelerator.config.button.report_bug.tooltip",
                "vhaccelerator.config.dialog.default.title",
                "vhaccelerator.config.dialog.default.message",
                "vhaccelerator.config.dialog.experimental.title",
                "vhaccelerator.config.dialog.experimental.message",
                "vhaccelerator.config.dialog.experimental.none",
                "vhaccelerator.config.dialog.restart.title",
                "vhaccelerator.config.dialog.restart.message",
                "vhaccelerator.config.dialog.more",
                "vhaccelerator.config.message.cloth_missing",
                "vhaccelerator.config.message.cloth_incompatible",
                "vhaccelerator.bugreport.title",
                "vhaccelerator.bugreport.issue_heading",
                "vhaccelerator.bugreport.crash_heading",
                "vhaccelerator.bugreport.no_crash",
                "vhaccelerator.bugreport.button.copy_and_open",
                "vhaccelerator.bugreport.button.open_without_crash",
                "vhaccelerator.bugreport.button.open",
                "vhaccelerator.bugreport.status.hint"
        ));
        for (Category category : Category.values()) {
            required.add(category.translationKey());
        }
        for (Setting setting : ConfigSettingCatalog.all()) {
            if (ConfigSettingStore.defaultValue(setting) instanceof Enum<?> constant) {
                for (Object choice : constant.getDeclaringClass().getEnumConstants()) {
                    required.add(ConfigSettingCatalog.LANG_PREFIX + "enum."
                            + ((Enum<?>) choice).name().toLowerCase(java.util.Locale.ROOT));
                }
            }
            required.add(setting.labelKey());
            required.add(setting.summaryKey());
            required.add(setting.tooltipKey());
        }
        for (String key : required) {
            assertTrue(lang.has(key), "en_us.json lacks " + key);
            assertFalse(lang.get(key).getAsString().isBlank(), "en_us.json has blank " + key);
        }
    }

    @Test
    void labelsAndSummariesFitTheWordingLimits() throws Exception {
        JsonObject lang;
        try (InputStream stream = ConfigSettingCatalogTest.class.getResourceAsStream(
                "/assets/vhaccelerator/lang/en_us.json")) {
            lang = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for (Setting setting : ConfigSettingCatalog.all()) {
            String label = lang.get(setting.labelKey()).getAsString();
            String summary = lang.get(setting.summaryKey()).getAsString();
            assertTrue(label.length() <= 32, "label over 32 characters: " + setting.labelKey());
            assertTrue(summary.length() <= 70, "summary over 70 characters: " + setting.summaryKey());
        }
    }

    @Test
    void experimentalIsDefaultOffBooleansOutsideDiagnosticsAndExclusions() {
        Set<Setting> expected = new HashSet<>();
        for (Setting setting : ConfigSettingCatalog.all()) {
            Object defaultValue = ConfigSettingStore.defaultValue(setting);
            if (Boolean.FALSE.equals(defaultValue) && setting.category() != Category.DIAGNOSTICS
                    && !ConfigSettingCatalog.EXPERIMENTAL_EXCLUSIONS.contains(setting.id())) {
                expected.add(setting);
            }
        }

        List<Setting> experimental = ConfigSettingStore.experimentalTargets();
        assertEquals(expected, new HashSet<>(experimental));
        assertTrue(experimental.stream().noneMatch(Setting::diagnostics));
        assertTrue(experimental.stream().noneMatch(setting -> ConfigSettingCatalog.EXPERIMENTAL_EXCLUSIONS.contains(setting.id())));
        assertEquals(EXPERIMENTAL_IDS, experimental.stream().map(Setting::id).collect(Collectors.toSet()));
    }

    @Test
    void restartClassificationMatchesTheCode() {
        Map<String, Boolean> restart = new HashMap<>();
        ConfigSettingCatalog.all().forEach(setting -> restart.put(setting.id(), setting.restartRequired()));
        assertTrue(restart.get("asyncChunkDiskReads"), "asyncChunkDiskReads needs a restart");
        assertTrue(restart.get("asyncCrashReportPreload"), "asyncCrashReportPreload needs a restart");
        assertTrue(restart.get("asyncUserApiService"), "asyncUserApiService needs a restart");
        assertTrue(restart.get("attachCapabilitiesDispatch"), "attachCapabilitiesDispatch needs a restart");
        assertTrue(restart.get("attributeSupplierDeduplication"), "attributeSupplierDeduplication needs a restart");
        assertTrue(restart.get("backgroundWorkerLimit"), "backgroundWorkerLimit needs a restart");
        assertTrue(restart.get("blockPropertyNameDeduplication"), "blockPropertyNameDeduplication needs a restart");
        assertTrue(restart.get("cacheBlockStateModelLocations"), "cacheBlockStateModelLocations needs a restart");
        assertTrue(restart.get("cacheDecocraftBbModels"), "cacheDecocraftBbModels needs a restart");
        assertTrue(restart.get("cacheLaunchVoxelShapes"), "cacheLaunchVoxelShapes needs a restart");
        assertTrue(restart.get("compactEntityModels"), "compactEntityModels needs a restart");
        assertTrue(restart.get("compactFerriteCorePropertyMaps"), "compactFerriteCorePropertyMaps needs a restart");
        assertTrue(restart.get("compactModFileScanData"), "compactModFileScanData needs a restart");
        assertTrue(restart.get("compactModelFaceLists"), "compactModelFaceLists needs a restart");
        assertTrue(restart.get("compactObjectHolderThrowables"), "compactObjectHolderThrowables needs a restart");
        assertTrue(restart.get("compactPaletteValidation"), "compactPaletteValidation needs a restart");
        assertTrue(restart.get("compareMode"), "compareMode needs a restart");
        assertTrue(restart.get("debug"), "debug needs a restart");
        assertTrue(restart.get("debugLevelSourceStateView"), "debugLevelSourceStateView needs a restart");
        assertTrue(restart.get("deduplicateModelLocationPaths"), "deduplicateModelLocationPaths needs a restart");
        assertTrue(restart.get("deduplicateModelMaterialCollection"), "deduplicateModelMaterialCollection needs a restart");
        assertTrue(restart.get("deduplicateResourceLocationNamespaces"), "deduplicateResourceLocationNamespaces needs a restart");
        assertTrue(restart.get("deduplicateWallShapes"), "deduplicateWallShapes needs a restart");
        assertTrue(restart.get("deferBlockStateModelBaking"), "deferBlockStateModelBaking needs a restart");
        assertTrue(restart.get("deferItemModelBaking"), "deferItemModelBaking needs a restart");
        assertTrue(restart.get("deferTargetDummyDispenserRegistration"), "deferTargetDummyDispenserRegistration needs a restart");
        assertTrue(restart.get("deferVaultAtlasUploads"), "deferVaultAtlasUploads needs a restart");
        assertTrue(restart.get("deferXaeroOnlineChecks"), "deferXaeroOnlineChecks needs a restart");
        assertTrue(restart.get("disableEveryCompatDebugResourceDump"), "disableEveryCompatDebugResourceDump needs a restart");
        assertTrue(restart.get("disableTelemetry"), "disableTelemetry needs a restart");
        assertTrue(restart.get("dynamicClientLanguages"), "dynamicClientLanguages needs a restart");
        assertTrue(restart.get("earlyStructureLocationRejection"), "earlyStructureLocationRejection needs a restart");
        assertTrue(restart.get("enableClientOptimizations"), "enableClientOptimizations needs a restart");
        assertTrue(restart.get("enableCommonOptimizations"), "enableCommonOptimizations needs a restart");
        assertTrue(restart.get("fastRegistryFreezeCheck"), "fastRegistryFreezeCheck needs a restart");
        assertTrue(restart.get("fasterIngredientEmptinessCheck"), "fasterIngredientEmptinessCheck needs a restart");
        assertTrue(restart.get("fasterIngredientExpansionCache"), "fasterIngredientExpansionCache needs a restart");
        assertTrue(restart.get("fasterIngredientTagLookups"), "fasterIngredientTagLookups needs a restart");
        assertTrue(restart.get("fasterLootLoading"), "fasterLootLoading needs a restart");
        assertTrue(restart.get("forgeHandshakeBatching"), "forgeHandshakeBatching needs a restart");
        assertTrue(restart.get("forgeRegistryLambdaElision"), "forgeRegistryLambdaElision needs a restart");
        assertTrue(restart.get("forgeRegistryRegistrationAcceleration"), "forgeRegistryRegistrationAcceleration needs a restart");
        assertTrue(restart.get("forgeTagConcurrencyFixes"), "forgeTagConcurrencyFixes needs a restart");
        assertTrue(restart.get("indexImmutableModResources"), "indexImmutableModResources needs a restart");
        assertTrue(restart.get("indexModelBakeRegistries"), "indexModelBakeRegistries needs a restart");
        assertTrue(restart.get("ingredientItemValueDeduplication"), "ingredientItemValueDeduplication needs a restart");
        assertTrue(restart.get("isolateBackgroundNetworkWork"), "isolateBackgroundNetworkWork needs a restart");
        assertTrue(restart.get("lazyBakeEventModels"), "lazyBakeEventModels needs a restart");
        assertTrue(restart.get("lazyBlockStateCache"), "lazyBlockStateCache needs a restart");
        assertTrue(restart.get("mappedRegistryGrowth"), "mappedRegistryGrowth needs a restart");
        assertTrue(restart.get("memoizeCtmModelBakeTraversal"), "memoizeCtmModelBakeTraversal needs a restart");
        assertTrue(restart.get("memoizeModelMaterials"), "memoizeModelMaterials needs a restart");
        assertTrue(restart.get("modernFixIntegratedWatchdogCorrection"), "modernFixIntegratedWatchdogCorrection needs a restart");
        assertTrue(restart.get("modernFixJeiSearchSnapshot"), "modernFixJeiSearchSnapshot needs a restart");
        assertTrue(restart.get("modernFixNightConfigWatcherCorrection"), "modernFixNightConfigWatcherCorrection needs a restart");
        assertTrue(restart.get("optimizeMaterialCacheSession"), "optimizeMaterialCacheSession needs a restart");
        assertTrue(restart.get("optimizeVoxelShapeMerging"), "optimizeVoxelShapeMerging needs a restart");
        assertTrue(restart.get("overlapModelPreparation"), "overlapModelPreparation needs a restart");
        assertTrue(restart.get("overrideModernFixDynamicResources"), "overrideModernFixDynamicResources needs a restart");
        assertTrue(restart.get("parallelAtlasStitching"), "parallelAtlasStitching needs a restart");
        assertTrue(restart.get("parallelBlockModelCache"), "parallelBlockModelCache needs a restart");
        assertTrue(restart.get("parallelBlockStateInit"), "parallelBlockStateInit needs a restart");
        assertTrue(restart.get("parallelBlockStateLoading"), "parallelBlockStateLoading needs a restart");
        assertTrue(restart.get("parallelBlockStateModelLocations"), "parallelBlockStateModelLocations needs a restart");
        assertTrue(restart.get("parallelKubeJsRecipeFilters"), "parallelKubeJsRecipeFilters needs a restart");
        assertTrue(restart.get("parallelModelBaking"), "parallelModelBaking needs a restart");
        assertTrue(restart.get("parallelModelLoading"), "parallelModelLoading needs a restart");
        assertTrue(restart.get("persistentBlockStateJsonCache"), "persistentBlockStateJsonCache needs a restart");
        assertTrue(restart.get("persistentEveryCompatPack"), "persistentEveryCompatPack needs a restart");
        assertTrue(restart.get("persistentModelJsonCache"), "persistentModelJsonCache needs a restart");
        assertTrue(restart.get("persistentModelMaterialCache"), "persistentModelMaterialCache needs a restart");
        assertTrue(restart.get("potentialSpawnCopyOnWrite"), "potentialSpawnCopyOnWrite needs a restart");
        assertTrue(restart.get("preSizeFerriteCoreQuadCache"), "preSizeFerriteCoreQuadCache needs a restart");
        assertTrue(restart.get("preSizeModelCaches"), "preSizeModelCaches needs a restart");
        assertTrue(restart.get("prewarmPersistentPlainModels"), "prewarmPersistentPlainModels needs a restart");
        assertTrue(restart.get("profileClientLaunchPhases"), "profileClientLaunchPhases needs a restart");
        assertTrue(restart.get("promoteCachedTopLevelModels"), "promoteCachedTopLevelModels needs a restart");
        assertTrue(restart.get("protectDynamicModels"), "protectDynamicModels needs a restart");
        assertTrue(restart.get("recordBlockStateMaterialManifest"), "recordBlockStateMaterialManifest needs a restart");
        assertTrue(restart.get("reduceTickingChunkAllocations"), "reduceTickingChunkAllocations needs a restart");
        assertTrue(restart.get("releaseBakeryLoadMaps"), "releaseBakeryLoadMaps needs a restart");
        assertTrue(restart.get("releaseCacheMemoryAfterUse"), "releaseCacheMemoryAfterUse needs a restart");
        assertTrue(restart.get("releaseLevelPinningReferences"), "releaseLevelPinningReferences needs a restart");
        assertTrue(restart.get("removeRedundantObjectHolderCallbacks"), "removeRedundantObjectHolderCallbacks needs a restart");
        assertTrue(restart.get("resourceKeyInterning"), "resourceKeyInterning needs a restart");
        assertTrue(restart.get("responsiveModWorkQueue"), "responsiveModWorkQueue needs a restart");
        assertTrue(restart.get("restoreSmoothBootThreadPriorities"), "restoreSmoothBootThreadPriorities needs a restart");
        assertTrue(restart.get("separateModelPrewarmIo"), "separateModelPrewarmIo needs a restart");
        assertTrue(restart.get("serializeAtlasStitchEvents"), "serializeAtlasStitchEvents needs a restart");
        assertTrue(restart.get("serverEventLoopFix"), "serverEventLoopFix needs a restart");
        assertTrue(restart.get("skipBlockStateGraphLoading"), "skipBlockStateGraphLoading needs a restart");
        assertTrue(restart.get("skipRedundantRegistryValidation"), "skipRedundantRegistryValidation needs a restart");
        assertTrue(restart.get("skipRegistryDump"), "skipRegistryDump needs a restart");
        assertTrue(restart.get("skipReturningPlayerSpawnSearch"), "skipReturningPlayerSpawnSearch needs a restart");
        assertTrue(restart.get("stageModelCacheSizing"), "stageModelCacheSizing needs a restart");
        assertTrue(restart.get("stateDefinitionConstruction"), "stateDefinitionConstruction needs a restart");
        assertTrue(restart.get("streamlineObjectHolderCleanup"), "streamlineObjectHolderCleanup needs a restart");
        assertTrue(restart.get("strongholdRingEarlyRejection"), "strongholdRingEarlyRejection needs a restart");
        assertTrue(restart.get("takeOverBuildScapeModelLoading"), "takeOverBuildScapeModelLoading needs a restart");
        assertTrue(restart.get("trimDecocraftModels"), "trimDecocraftModels needs a restart");
        assertTrue(restart.get("worldgenBiomeSupplierReuse"), "worldgenBiomeSupplierReuse needs a restart");
        assertTrue(restart.get("worldgenDeferredClimateTree"), "worldgenDeferredClimateTree needs a restart");
        assertTrue(restart.get("worldgenDirectYConditions"), "worldgenDirectYConditions needs a restart");
        assertTrue(restart.get("worldgenMaterialRuleIteration"), "worldgenMaterialRuleIteration needs a restart");
        assertTrue(restart.get("worldgenNoiseFunctionCache"), "worldgenNoiseFunctionCache needs a restart");
        assertTrue(restart.get("worldgenSurfaceRuleIteration"), "worldgenSurfaceRuleIteration needs a restart");
        assertFalse(restart.get("asyncJeiSearchIndex"), "asyncJeiSearchIndex applies without a restart");
        assertFalse(restart.get("boundFarsightChunkRetention"), "boundFarsightChunkRetention applies without a restart");
        assertFalse(restart.get("cacheIronFurnacesJeiRecipes"), "cacheIronFurnacesJeiRecipes applies without a restart");
        assertFalse(restart.get("cacheJerCompatibility"), "cacheJerCompatibility applies without a restart");
        assertFalse(restart.get("cacheResourceListing"), "cacheResourceListing applies without a restart");
        assertFalse(restart.get("cacheVaultModifierViews"), "cacheVaultModifierViews applies without a restart");
        assertFalse(restart.get("cacheVaultTooltips"), "cacheVaultTooltips applies without a restart");
        assertFalse(restart.get("checkForUpdates"), "checkForUpdates applies without a restart");
        assertFalse(restart.get("compactCraftTweakerClientReplayLogging"), "compactCraftTweakerClientReplayLogging applies without a restart");
        assertFalse(restart.get("filterVaultCascadeByState"), "filterVaultCascadeByState applies without a restart");
        assertFalse(restart.get("indexCreateBlockCuttingRecipes"), "indexCreateBlockCuttingRecipes applies without a restart");
        assertFalse(restart.get("indexKubeJsPackFiles"), "indexKubeJsPackFiles applies without a restart");
        assertFalse(restart.get("indexPowahWikiRecipes"), "indexPowahWikiRecipes applies without a restart");
        assertFalse(restart.get("indexVaultCascadeModifiers"), "indexVaultCascadeModifiers applies without a restart");
        assertFalse(restart.get("indexVaultModifierTicks"), "indexVaultModifierTicks applies without a restart");
        assertFalse(restart.get("indexVaultSmeltingJeiRecipes"), "indexVaultSmeltingJeiRecipes applies without a restart");
        assertFalse(restart.get("ironFurnacesPrecompileFrameBudgetMillis"), "ironFurnacesPrecompileFrameBudgetMillis applies without a restart");
        assertFalse(restart.get("jeiRecipeAudit"), "jeiRecipeAudit applies without a restart");
        assertFalse(restart.get("jeiTweakerParallelThreshold"), "jeiTweakerParallelThreshold applies without a restart");
        assertFalse(restart.get("lazyGeckoLibResources"), "lazyGeckoLibResources applies without a restart");
        assertFalse(restart.get("lazyVaultLootCdf"), "lazyVaultLootCdf applies without a restart");
        assertFalse(restart.get("optimizeIndustrialForegoingStoneWorkJeiRecipes"), "optimizeIndustrialForegoingStoneWorkJeiRecipes applies without a restart");
        assertFalse(restart.get("optimizeJeiIngredientFilterConstruction"), "optimizeJeiIngredientFilterConstruction applies without a restart");
        assertFalse(restart.get("optimizeVaultAtlasValidation"), "optimizeVaultAtlasValidation applies without a restart");
        assertFalse(restart.get("optimizeVaultLootCdf"), "optimizeVaultLootCdf applies without a restart");
        assertFalse(restart.get("parallelCraftTweakerRecipeRemoval"), "parallelCraftTweakerRecipeRemoval applies without a restart");
        assertFalse(restart.get("parallelCraftTweakerTagBinding"), "parallelCraftTweakerTagBinding applies without a restart");
        assertFalse(restart.get("parallelJeiIngredientSorting"), "parallelJeiIngredientSorting applies without a restart");
        assertFalse(restart.get("parallelJeiSearchPrefixes"), "parallelJeiSearchPrefixes applies without a restart");
        assertFalse(restart.get("parallelJeiTweakerMatching"), "parallelJeiTweakerMatching applies without a restart");
        assertFalse(restart.get("parallelReloadPreparation"), "parallelReloadPreparation applies without a restart");
        assertFalse(restart.get("parallelThermalRecipeRefresh"), "parallelThermalRecipeRefresh applies without a restart");
        assertFalse(restart.get("parallelVanillaRecipeValidation"), "parallelVanillaRecipeValidation applies without a restart");
        assertFalse(restart.get("persistentIronFurnacesFuelCache"), "persistentIronFurnacesFuelCache applies without a restart");
        assertFalse(restart.get("persistentJeiRecipeIndexCache"), "persistentJeiRecipeIndexCache applies without a restart");
        assertFalse(restart.get("persistentVanillaIngredientCache"), "persistentVanillaIngredientCache applies without a restart");
        assertFalse(restart.get("persistentVanillaRecipeValidationCache"), "persistentVanillaRecipeValidationCache applies without a restart");
        assertFalse(restart.get("precompileIronFurnacesJeiRecipes"), "precompileIronFurnacesJeiRecipes applies without a restart");
        assertFalse(restart.get("prefetchCtmTextureMetadata"), "prefetchCtmTextureMetadata applies without a restart");
        assertFalse(restart.get("prefetchJoinRecipeFingerprint"), "prefetchJoinRecipeFingerprint applies without a restart");
        assertFalse(restart.get("repairEmptyBookPiles"), "repairEmptyBookPiles applies without a restart");
        assertFalse(restart.get("snapshotVaultEventListeners"), "snapshotVaultEventListeners applies without a restart");
        assertFalse(restart.get("stagedVaultGroupLoading"), "stagedVaultGroupLoading applies without a restart");
        assertFalse(restart.get("suspendIntegratedServerDuringJoin"), "suspendIntegratedServerDuringJoin applies without a restart");
        assertFalse(restart.get("timers"), "timers applies without a restart");
        assertFalse(restart.get("updateTypes"), "updateTypes applies without a restart");
        assertFalse(restart.get("vaultGroupTickBudgetMillis"), "vaultGroupTickBudgetMillis applies without a restart");
        assertEquals(ConfigSettingCatalog.all().size(), restart.size());
    }

    @Test
    void integerSettingsExposeTheirSpecRange() {
        Setting budget = ConfigSettingCatalog.all().stream()
                .filter(setting -> setting.id().equals("vaultGroupTickBudgetMillis")).findFirst().orElseThrow();
        int[] range = ConfigSettingStore.intRange(budget);
        assertNotNull(range);
        assertEquals(1, range[0]);
        assertEquals(25, range[1]);
        assertEquals(4, ConfigSettingStore.defaultValue(budget));
    }

    private static void collectValuePaths(UnmodifiableConfig config, List<String> out) {
        for (Object value : config.valueMap().values()) {
            if (value instanceof UnmodifiableConfig child) {
                collectValuePaths(child, out);
            } else if (value instanceof ForgeConfigSpec.ConfigValue<?> configValue) {
                out.add(String.join(".", configValue.getPath()));
            }
        }
    }
}
