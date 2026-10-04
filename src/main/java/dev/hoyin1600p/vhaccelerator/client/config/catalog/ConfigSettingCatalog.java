package dev.hoyin1600p.vhaccelerator.client.config.catalog;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClient;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.update.UpdateNoticeFilter;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The one list of player-facing settings: which settings-screen category each value belongs to,
 * where it is stored, whether a change needs a restart, and how it applies live. The settings
 * screen, Default and Experimental all read it; tests check it covers every config value once.
 *
 * <p>Every value of both config specs appears exactly once here or in {@link #UNLISTED_PATHS}.
 */
public final class ConfigSettingCatalog {
    /** Prefix of every settings-screen translation key. */
    public static final String LANG_PREFIX = "vhaccelerator.config.";

    /** Settings-screen tabs, in display order. Player-facing groups, not the .toml sections. */
    public enum Category {
        // Player-facing groups. DIAGNOSTICS stays last; the bug-report button lives there.
        GENERAL("general"),
        LAUNCH("launch"),
        JOIN("join"),
        VAULT("vault"),
        COMPAT("compat"),
        MEMORY("memory"),
        SERVER("server"),
        BACKPORTS("backports"),
        DIAGNOSTICS("diagnostics");

        private final String id;

        Category(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return LANG_PREFIX + "category." + id;
        }
    }

    /**
     * Which config file a value lives in.
     */
    public enum Storage {
        /** {@code config/vhaccelerator-client.toml}, {@link VHAcceleratorClientConfig#SPEC}. */
        CLIENT_TOML,
        /** {@code config/vhaccelerator-common.toml}, {@link VHAcceleratorConfig#COMMON_SPEC}. */
        COMMON_TOML
    }

    /**
     * One setting. {@code path} is the .toml path ({@code ForgeConfigSpec.ConfigValue.getPath()}).
     * {@code liveSetter}, when present, is the mod's existing setter whose side effects (cache
     * clears, renderer reloads, re-registration) must run on a change; every other value applies
     * when the spec value is set and saved, because VHA reads its config values on each use.
     */
    public record Setting(
            String id,
            Category category,
            Storage storage,
            List<String> path,
            boolean restartRequired,
            Consumer<Object> liveSetter
    ) {
        public boolean diagnostics() {
            return category == Category.DIAGNOSTICS;
        }

        public String labelKey() {
            return LANG_PREFIX + category.id() + "." + id;
        }

        public String summaryKey() {
            return labelKey() + ".summary";
        }

        public String tooltipKey() {
            return labelKey() + ".tooltip";
        }
    }

    /**
     * Dotted .toml paths deliberately left off the screen: values the mod never reads, lists and
     * strings with no sensible control, or values deliberately kept off the screen. Default and
     * Experimental leave them as they are in the file. Each entry needs a reason in a comment.
     */
    public static final Set<String> UNLISTED_PATHS = Set.of(
    );

    /**
     * Default-off settings the Experimental button never turns on, by setting id: deliberate
     * trade-offs that reduce visuals, distance or detail rather than experiments. Each entry
     * needs a reason in a comment.
     */
    public static final Set<String> EXPERIMENTAL_EXCLUSIONS = Set.of(
            // Parallel atlas stitching has produced wrong textures in Vault packs; it stays a
            // manual opt-in until it is made safe.
            "parallelAtlasStitching"
    );

    private static final List<Setting> SETTINGS = build();

    private ConfigSettingCatalog() {
    }

    public static List<Setting> all() {
        return SETTINGS;
    }

    public static List<Setting> in(Category category) {
        return SETTINGS.stream().filter(setting -> setting.category() == category).toList();
    }

    private static List<Setting> build() {
        List<Setting> settings = new ArrayList<>();
        // Restart flags come from where each value is read: values read only during launch, mod
        // loading or mixin selection need a restart; values read with get() on each use apply
        // at once (some on the next world join or resource reload, as their explanations say).

        // General
        // partly live; the explanation says which part needs a restart
        add(settings, "enableClientOptimizations", Category.GENERAL, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "checkForUpdates", Category.GENERAL, Storage.CLIENT_TOML, "updates", false,
                value -> VHAcceleratorClient.setUpdateChecksEnabled((Boolean) value));
        add(settings, "updateTypes", Category.GENERAL, Storage.CLIENT_TOML, "updates", false,
                value -> VHAcceleratorClient.setUpdateNoticeFilter((UpdateNoticeFilter) value));
        // partly live; the explanation says which part needs a restart
        add(settings, "enableCommonOptimizations", Category.GENERAL, Storage.COMMON_TOML, "optimizations", true, null);

        // Launch & loading
        add(settings, "overlapModelPreparation", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "parallelModelLoading", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "parallelBlockStateLoading", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "parallelAtlasStitching", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "serializeAtlasStitchEvents", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "parallelModelBaking", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "optimizeVoxelShapeMerging", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "cacheLaunchVoxelShapes", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "asyncCrashReportPreload", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "persistentModelJsonCache", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "prewarmPersistentPlainModels", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "separateModelPrewarmIo", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "optimizeMaterialCacheSession", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "isolateBackgroundNetworkWork", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "persistentBlockStateJsonCache", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "preSizeModelCaches", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "stageModelCacheSizing", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "promoteCachedTopLevelModels", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "asyncUserApiService", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "memoizeModelMaterials", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "persistentModelMaterialCache", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "deduplicateModelMaterialCollection", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "cacheBlockStateModelLocations", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "lazyBakeEventModels", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "parallelBlockStateModelLocations", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "parallelBlockModelCache", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "deferItemModelBaking", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "deferBlockStateModelBaking", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "skipBlockStateGraphLoading", Category.LAUNCH, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "protectDynamicModels", Category.LAUNCH, Storage.CLIENT_TOML, "compatibility", true, null);
        add(settings, "indexModelBakeRegistries", Category.LAUNCH, Storage.CLIENT_TOML, "compatibility", true, null);
        add(settings, "cacheResourceListing", Category.LAUNCH, Storage.COMMON_TOML, "optimizations", false, null);

        // Joining worlds & servers
        add(settings, "prefetchJoinRecipeFingerprint", Category.JOIN, Storage.CLIENT_TOML, "optimizations", false, null);
        add(settings, "suspendIntegratedServerDuringJoin", Category.JOIN, Storage.CLIENT_TOML, "optimizations", false, null);
        add(settings, "parallelJeiIngredientSorting", Category.JOIN, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "asyncJeiSearchIndex", Category.JOIN, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "parallelJeiSearchPrefixes", Category.JOIN, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "optimizeJeiIngredientFilterConstruction", Category.JOIN, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "persistentVanillaIngredientCache", Category.JOIN, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "parallelVanillaRecipeValidation", Category.JOIN, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "persistentVanillaRecipeValidationCache", Category.JOIN, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "persistentJeiRecipeIndexCache", Category.JOIN, Storage.CLIENT_TOML, "compatibility", false, null);

        // Vault Hunters
        add(settings, "indexVaultSmeltingJeiRecipes", Category.VAULT, Storage.CLIENT_TOML, "optimizations", false, null);
        add(settings, "indexVaultCascadeModifiers", Category.VAULT, Storage.CLIENT_TOML, "optimizations", false, null);
        add(settings, "cacheVaultModifierViews", Category.VAULT, Storage.CLIENT_TOML, "optimizations", false, null);
        add(settings, "indexVaultModifierTicks", Category.VAULT, Storage.CLIENT_TOML, "optimizations", false, null);
        add(settings, "snapshotVaultEventListeners", Category.VAULT, Storage.CLIENT_TOML, "optimizations", false, null);
        add(settings, "filterVaultCascadeByState", Category.VAULT, Storage.CLIENT_TOML, "optimizations", false, null);
        add(settings, "repairEmptyBookPiles", Category.VAULT, Storage.CLIENT_TOML, "optimizations", false, null);
        add(settings, "stagedVaultGroupLoading", Category.VAULT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "optimizeVaultLootCdf", Category.VAULT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "lazyVaultLootCdf", Category.VAULT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "vaultGroupTickBudgetMillis", Category.VAULT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "deferVaultAtlasUploads", Category.VAULT, Storage.CLIENT_TOML, "compatibility", true, null);
        add(settings, "cacheVaultTooltips", Category.VAULT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "optimizeVaultAtlasValidation", Category.VAULT, Storage.CLIENT_TOML, "compatibility", false, null);

        // Mod compatibility
        add(settings, "takeOverBuildScapeModelLoading", Category.COMPAT, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "preSizeFerriteCoreQuadCache", Category.COMPAT, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "parallelCraftTweakerRecipeRemoval", Category.COMPAT, Storage.CLIENT_TOML, "optimizations", false, null);
        add(settings, "indexCreateBlockCuttingRecipes", Category.COMPAT, Storage.CLIENT_TOML, "optimizations", false, null);
        add(settings, "prefetchCtmTextureMetadata", Category.COMPAT, Storage.CLIENT_TOML, "optimizations", false, null);
        add(settings, "indexKubeJsPackFiles", Category.COMPAT, Storage.CLIENT_TOML, "optimizations", false, null);
        add(settings, "lazyGeckoLibResources", Category.COMPAT, Storage.CLIENT_TOML, "optimizations", false, null);
        add(settings, "persistentEveryCompatPack", Category.COMPAT, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "memoizeCtmModelBakeTraversal", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", true, null);
        add(settings, "disableEveryCompatDebugResourceDump", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", true, null);
        add(settings, "cacheDecocraftBbModels", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", true, null);
        add(settings, "indexPowahWikiRecipes", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "parallelJeiTweakerMatching", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "jeiTweakerParallelThreshold", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "cacheJerCompatibility", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "parallelCraftTweakerTagBinding", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "compactCraftTweakerClientReplayLogging", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "parallelThermalRecipeRefresh", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "cacheIronFurnacesJeiRecipes", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "persistentIronFurnacesFuelCache", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "precompileIronFurnacesJeiRecipes", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "ironFurnacesPrecompileFrameBudgetMillis", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "optimizeIndustrialForegoingStoneWorkJeiRecipes", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "deferXaeroOnlineChecks", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", true, null);
        add(settings, "boundFarsightChunkRetention", Category.COMPAT, Storage.CLIENT_TOML, "compatibility", false, null);
        add(settings, "deferTargetDummyDispenserRegistration", Category.COMPAT, Storage.COMMON_TOML, "compatibility", true, null);
        add(settings, "restoreSmoothBootThreadPriorities", Category.COMPAT, Storage.COMMON_TOML, "compatibility", true, null);
        add(settings, "parallelKubeJsRecipeFilters", Category.COMPAT, Storage.COMMON_TOML, "optimizations", true, null);

        // Memory
        // partly live; the explanation says which part needs a restart
        add(settings, "releaseCacheMemoryAfterUse", Category.MEMORY, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "releaseBakeryLoadMaps", Category.MEMORY, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "compactModelFaceLists", Category.MEMORY, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "deduplicateModelLocationPaths", Category.MEMORY, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "trimDecocraftModels", Category.MEMORY, Storage.COMMON_TOML, "compatibility", true, null);
        add(settings, "compactFerriteCorePropertyMaps", Category.MEMORY, Storage.COMMON_TOML, "compatibility", true, null);
        // partly live; the explanation says which part needs a restart
        add(settings, "releaseLevelPinningReferences", Category.MEMORY, Storage.COMMON_TOML, "compatibility", true, null);

        // Server & engine
        add(settings, "streamlineObjectHolderCleanup", Category.SERVER, Storage.COMMON_TOML, "optimizations", true, null);
        add(settings, "parallelReloadPreparation", Category.SERVER, Storage.COMMON_TOML, "optimizations", false, null);
        add(settings, "skipRedundantRegistryValidation", Category.SERVER, Storage.COMMON_TOML, "optimizations", true, null);
        add(settings, "skipRegistryDump", Category.SERVER, Storage.COMMON_TOML, "optimizations", true, null);
        add(settings, "parallelBlockStateInit", Category.SERVER, Storage.COMMON_TOML, "optimizations", true, null);
        add(settings, "lazyBlockStateCache", Category.SERVER, Storage.COMMON_TOML, "optimizations", true, null);
        add(settings, "indexImmutableModResources", Category.SERVER, Storage.COMMON_TOML, "optimizations", true, null);
        add(settings, "asyncChunkDiskReads", Category.SERVER, Storage.COMMON_TOML, "optimizations", true, null);
        add(settings, "skipReturningPlayerSpawnSearch", Category.SERVER, Storage.COMMON_TOML, "optimizations", true, null);

        // Backports
        add(settings, "forgeHandshakeBatching", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "attributeSupplierDeduplication", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "attachCapabilitiesDispatch", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "compactModFileScanData", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "forgeTagConcurrencyFixes", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "serverEventLoopFix", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "overrideModernFixDynamicResources", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "responsiveModWorkQueue", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "modernFixIntegratedWatchdogCorrection", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "stateDefinitionConstruction", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "compactPaletteValidation", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "modernFixJeiSearchSnapshot", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "modernFixNightConfigWatcherCorrection", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "backgroundWorkerLimit", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "compactEntityModels", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "worldgenMaterialRuleIteration", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "worldgenSurfaceRuleIteration", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "worldgenNoiseFunctionCache", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "worldgenBiomeSupplierReuse", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "worldgenDirectYConditions", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "worldgenDeferredClimateTree", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "earlyStructureLocationRejection", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "compactObjectHolderThrowables", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "removeRedundantObjectHolderCallbacks", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "fasterLootLoading", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "dynamicClientLanguages", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "disableTelemetry", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "fasterIngredientTagLookups", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "fasterIngredientExpansionCache", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "ingredientItemValueDeduplication", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "mappedRegistryGrowth", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "forgeRegistryLambdaElision", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "forgeRegistryRegistrationAcceleration", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "deduplicateResourceLocationNamespaces", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "blockPropertyNameDeduplication", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "deduplicateWallShapes", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "potentialSpawnCopyOnWrite", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "reduceTickingChunkAllocations", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "resourceKeyInterning", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "fasterIngredientEmptinessCheck", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "debugLevelSourceStateView", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "fastRegistryFreezeCheck", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);
        add(settings, "strongholdRingEarlyRejection", Category.BACKPORTS, Storage.COMMON_TOML, "backports", true, null);

        // Diagnostics
        add(settings, "recordBlockStateMaterialManifest", Category.DIAGNOSTICS, Storage.CLIENT_TOML, "optimizations", true, null);
        add(settings, "profileClientLaunchPhases", Category.DIAGNOSTICS, Storage.CLIENT_TOML, "diagnostics", true, null);
        add(settings, "compareMode", Category.DIAGNOSTICS, Storage.COMMON_TOML, "diagnostics", true,
                value -> VHAcceleratorConfig.setCompareMode((Boolean) value));
        add(settings, "timers", Category.DIAGNOSTICS, Storage.COMMON_TOML, "diagnostics", false, null);
        add(settings, "debug", Category.DIAGNOSTICS, Storage.COMMON_TOML, "diagnostics", true,
                value -> VHAcceleratorConfig.setDebugDiagnosticsEnabled((Boolean) value));
        add(settings, "jeiRecipeAudit", Category.DIAGNOSTICS, Storage.COMMON_TOML, "diagnostics", false, null);

        return Collections.unmodifiableList(settings);
    }

    private static void add(List<Setting> settings, String id, Category category, Storage storage,
                            String section, boolean restartRequired, Consumer<Object> liveSetter) {
        settings.add(new Setting(id, category, storage, List.of(section, id), restartRequired, liveSetter));
    }
}
