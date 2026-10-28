package dev.hoyin1600p.vhaccelerator.config;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCompareMode;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCommonConfig;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapDebugDiagnostics;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class VHAcceleratorConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    private VHAcceleratorConfig() {
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue compareMode;
        public final ForgeConfigSpec.BooleanValue timers;
        public final ForgeConfigSpec.BooleanValue debug;
        public final ForgeConfigSpec.BooleanValue jeiRecipeAudit;
        public final ForgeConfigSpec.BooleanValue startupJfr;
        public final ForgeConfigSpec.BooleanValue enableCommonOptimizations;
        public final ForgeConfigSpec.BooleanValue optimizeVoxelShapeMerging;
        public final ForgeConfigSpec.BooleanValue cacheLaunchVoxelShapes;
        public final ForgeConfigSpec.BooleanValue streamlineObjectHolderCleanup;
        public final ForgeConfigSpec.BooleanValue parallelReloadPreparation;
        public final ForgeConfigSpec.BooleanValue releaseMixinClassTrees;
        public final ForgeConfigSpec.BooleanValue skipRedundantRegistryValidation;
        public final ForgeConfigSpec.BooleanValue skipRegistryDump;
        public final ForgeConfigSpec.BooleanValue parallelBlockStateInit;
        public final ForgeConfigSpec.BooleanValue lazyBlockStateCache;
        public final ForgeConfigSpec.BooleanValue cacheResourceListing;
        public final ForgeConfigSpec.BooleanValue indexImmutableModResources;
        public final ForgeConfigSpec.BooleanValue indexCraftTweakerRecipeRemovals;
        public final ForgeConfigSpec.BooleanValue parallelKubeJsRecipeFilters;
        public final ForgeConfigSpec.BooleanValue indexKubeJsRecipeRemovals;
        public final ForgeConfigSpec.BooleanValue asyncChunkDiskReads;
        public final ForgeConfigSpec.BooleanValue indexObjectHolderDispatch;
        public final ForgeConfigSpec.BooleanValue lazyBlockStatePropertyCodecs;
        public final ForgeConfigSpec.BooleanValue presizeBlockStateIdMap;
        public final ForgeConfigSpec.BooleanValue skipReturningPlayerSpawnSearch;
        public final ForgeConfigSpec.BooleanValue deferTargetDummyDispenserRegistration;
        public final ForgeConfigSpec.BooleanValue optimizeVaultLootCdf;
        public final ForgeConfigSpec.BooleanValue lazyCompactVaultLootCdf;
        public final ForgeConfigSpec.BooleanValue fixBuddingCrystalChunkLoad;
        public final ForgeConfigSpec.BooleanValue trimDecocraftModels;
        public final ForgeConfigSpec.BooleanValue compactFerriteCorePropertyMaps;
        public final ForgeConfigSpec.BooleanValue compactBuildingGadgetsTemplates;
        public final ForgeConfigSpec.BooleanValue shareAe2ThingsDiskStrings;
        public final ForgeConfigSpec.BooleanValue compactWorldZonesMemberships;
        public final ForgeConfigSpec.BooleanValue shortCircuitLootJsRegexFilters;
        public final ForgeConfigSpec.BooleanValue deferThermalConvertedFuels;
        public final ForgeConfigSpec.BooleanValue releaseLevelPinningReferences;
        public final ForgeConfigSpec.BooleanValue restoreSmoothBootThreadPriorities;
        public final Map<BackportFeature, ForgeConfigSpec.BooleanValue> backports;

        private Common(ForgeConfigSpec.Builder builder) {
            builder.push("compatibility");
            optimizeVaultLootCdf = builder.comment(
                    "Vault Hunters 3.21.62: builds tiered-loot cumulative distributions with faster hash buckets.",
                    "Keeps the original entry order, floating-point calculations and loot generation.",
                    "Dedicated servers still build complete distributions eagerly. Applies to future builds",
                    "of the loot settings; already-built distributions are retained until the next config reload.",
                    "Disabled by Compare Mode and the common optimization master switch; clients also respect",
                    "the client optimization master switch. VHAPI, other versions or changed layouts keep the original method.")
                    .define("optimizeVaultLootCdf", true);
            lazyCompactVaultLootCdf = builder.comment(
                    "Dedicated servers with Vault Hunters 3.21.62 or 3.21.6.6884: computes each tiered-loot",
                    "distribution the first time loot generation reads it, then keeps it as one compact array",
                    "instead of a hash map (about 4.9 times smaller; about 125-135 MB less heap on a full pack).",
                    "Composes with VHAPI: whoever owns the arithmetic still computes the values; this only moves",
                    "when that runs and how the result is stored. Lookups return the same values bit for bit.",
                    "Applies to future builds of the loot settings. Disabled by Compare Mode and the common",
                    "optimization master switch. Other versions, other owners of the distribution's constructor,",
                    "get or getMap, or changed layouts keep the original eager hash map.")
                    .define("lazyCompactVaultLootCdf", true);
            fixBuddingCrystalChunkLoad = builder.comment(
                    "Prevents Vault Hunters budding crystals from loading neighbouring chunks during chunk loading.",
                    "Schedules initial growth one tick later and skips growth into unloaded chunks.",
                    "Integrated and dedicated server. Read live; no restart required.",
                    "Independent of Compare Mode and the optimization master switch: this is a correctness fix.")
                    .define("fixBuddingCrystalChunkLoad", true);
            deferTargetDummyDispenserRegistration = builder.comment(
                    "Fixes Target Dummy 1.18-1.5.2 writing the shared dispenser map during parallel setup.",
                    "Queues only its dispenser registration onto Forge's synchronous setup work.",
                    "Restart required. Independent of Compare Mode: this is a startup correctness fix.",
                    "Other Target Dummy versions are not modified.")
                    .define("deferTargetDummyDispenserRegistration", true);
            trimDecocraftModels = builder.comment(
                    "Trims Decocraft 3.0.4's parsed Blockbench models: block variants of the same",
                    "model file share one parse at registration, and the base64 images and file",
                    "paths Decocraft never reads again are cleared. Decocraft keeps one parsed copy",
                    "per block, so these took about 1 GB in Asgard. Models, textures and hit boxes",
                    "are unchanged. Restart required. Other Decocraft versions are not modified.")
                    .define("trimDecocraftModels", true);
            compactFerriteCorePropertyMaps = builder.comment(
                    "With FerriteCore 4.2.2's replacePropertyMap, drops the property-map object it",
                    "keeps for every block and fluid state (32 bytes each; 1.5 M states in large packs)",
                    "and answers property lookups from FerriteCore's shared FastMap directly.",
                    "getValues() builds FerriteCore's view on demand. Restart required.")
                    .define("compactFerriteCorePropertyMaps", true);
            compactBuildingGadgetsTemplates = builder.comment(
                    "Experimental: stores the block positions of supported BuildingGadgets templates in packed arrays.",
                    "Only dedicated servers with BuildingGadgets 3.13.2-build.21 and the exact supported layout.",
                    "Templates read exactly as before (same entries, order, block data and save format).",
                    "Default on (a paired server run showed lower memory use with no startup or autosave cost).",
                    "Restart required. Disabled by Compare Mode and the common optimization master switch.")
                    .define("compactBuildingGadgetsTemplates", true);
            compactWorldZonesMemberships = builder.comment(
                    "Dedicated-server Vault zone lookup compaction. Default on; restart required.",
                    "Stores reviewed singleton chunk memberships as primitive values; other sets stay original.",
                    "Saved zones, protection query order and network diffs are unchanged.",
                    "Only exact reviewed Vault classes are eligible. Disabled by Compare Mode and the common master.")
                    .define("compactWorldZonesMemberships", true);
            shareAe2ThingsDiskStrings = builder.comment(
                    "With AE2Things installed, shares equal text values (item ids, rarities and similar)",
                    "across the item data its DISK cells keep in memory after the cell data loads.",
                    "A large server holds about 1.5 million copies of a few thousand distinct values.",
                    "Values, key order and the saved data are unchanged. Restart required.",
                    "Disabled by Compare Mode and the common optimization master switch.")
                    .define("shareAe2ThingsDiskStrings", true);
            shortCircuitLootJsRegexFilters = builder.comment(
                    "With LootJS and KubeJS installed, a loot script filter written as a regular expression",
                    "(for example removeLoot(/.*botania.*/)) only asks whether any item matches, instead of",
                    "copying every matching item first. The answer, and every later loot result, is unchanged.",
                    "Only LootJS's own check on an exact KubeJS regex ingredient is touched; any other",
                    "ingredient or an unexpected class layout keeps the original path.",
                    "Restart required. Disabled by Compare Mode and the common optimization master switch.")
                    .define("shortCircuitLootJsRegexFilters", true);
            deferThermalConvertedFuels = builder.comment(
                    "Dedicated servers with Thermal Foundation: Thermal builds a list of every item that can burn",
                    "in a Stirling or Gourmand Dynamo at each tag update (a burn-time query for every registered",
                    "item, about 0.3 s on a Wolds-sized pack), but only Thermal's JEI plugin reads it. The list is",
                    "now built, by Thermal's own code, the first time something asks for it, and dropped by the next",
                    "refresh. The fuels the dynamos actually accept are not touched. An unexpected Thermal layout",
                    "keeps the original behavior. Restart required.",
                    "Disabled by Compare Mode and the common optimization master switch.")
                    .define("deferThermalConvertedFuels", true);
            releaseLevelPinningReferences = builder.comment(
                    "Stops references that keep a whole level in memory after leaving it: an item",
                    "entity stored on the shared empty item stack, Forge's static block-entity",
                    "model-data caches (cleared when the client clears its level), and Copycats+'s",
                    "static server supplier (which kept the whole integrated server).",
                    "Restart required.")
                    .define("releaseLevelPinningReferences", true);
            restoreSmoothBootThreadPriorities = builder.comment(
                    "When Smooth Boot (Reloaded) is installed, raises the Bootstrap, Main, IO and",
                    "modloading-worker threads it lowers (priority 1 by default) back to normal",
                    "priority 5, so VH Accelerator's parallel work is not starved under CPU contention.",
                    "Smooth Boot's thread counts are kept and priorities are only raised, never lowered.",
                    "Restart required. Disabled by Compare Mode.")
                    .define("restoreSmoothBootThreadPriorities", true);
            builder.pop();
            builder.push("diagnostics");
            compareMode = builder
                    .comment(
                            "Disables every VH Accelerator optimization while retaining",
                            "instrumentation selected by the separate timers and debug settings",
                            "for an unmodified baseline.",
                            "The /vha compare command changes and saves this setting.",
                            "Restart after changing it before comparing client or server launch time.")
                    .define("compareMode", false);
            timers = builder
                    .comment(
                            "Shows login, transfer, post-login, and disconnect timing",
                            "messages in chat and writes their routine timing summaries to",
                            "the log. The launch-time line remains visible on the main menu.",
                            "The /vha timers command changes this setting.",
                            "Internal lifecycle timestamps required for safe optimizations",
                            "remain available when this display setting is disabled.")
                    .define("timers", false);
            debug = builder
                    .comment(
                            "Enables detailed launch, reload, model, connection, packet,",
                            "and disconnect diagnostics. This adds measurement and logging",
                            "overhead and is disabled by default for normal play.",
                            "Diagnostic-only mixins are selected during bootstrap, so restart",
                            "after changing this setting with the /vha debug command.")
                    .define("debug", false);
            jeiRecipeAudit = builder
                    .comment(
                            "Logs exact recipe IDs and cached-versus-live plan differences",
                            "when the persistent JEI index repairs a recipe during login.",
                            "This targeted audit is disabled by default and is controlled by",
                            "the /vha jei_audit command independently of general debug logging.")
                    .define("jeiRecipeAudit", false);
            startupJfr = builder
                    .comment(
                            "Dedicated servers only. Records the launch with Java Flight Recorder and",
                            "writes logs/vhaccelerator/startup-<runId>.jfr about 30 seconds after the",
                            "started event finishes, so a silent stretch can be traced to the code that",
                            "ran. Uses the JDK profile settings with low thresholds for waits and I/O.",
                            "Size and age are bounded; it fails soft if the JVM has no JFR.",
                            "Restart required.")
                    .define("startupJfr", false);
            builder.pop();

            builder.push("optimizations");
            enableCommonOptimizations = builder
                    .comment("Master switch for optimizations that are safe on both client and dedicated server.")
                    .define("enableCommonOptimizations", true);
            optimizeVoxelShapeMerging = builder
                    .comment(
                            "Uses a flat-array coordinate merger for complex voxel shapes.",
                            "The output is equivalent to Minecraft's original merger and",
                            "can reduce block-registration time in decoration-heavy packs.",
                            "Clients and dedicated servers. Restart required.",
                            "Yields to Canary (which has an equivalent merger) and Lithium. Dedicated",
                            "servers respect Compare Mode and the common master switch; clients",
                            "respect their own client master switch.")
                    .define("optimizeVoxelShapeMerging", true);
            cacheLaunchVoxelShapes = builder
                    .comment(
                            "While mods load, shares equal voxel-shape boxes and reuses identical",
                            "shape joins, so decoration mods that build the same shapes for every",
                            "block and wood variant stop recomputing them. Shapes are immutable.",
                            "The cache is released when mod loading completes.",
                            "Clients and dedicated servers. Restart required.",
                            "Works beside Canary (its specialised boxes are shared the same way); yields",
                            "to Lithium. Dedicated servers respect Compare Mode and the common master",
                            "switch; clients respect their own client master switch.")
                    .define("cacheLaunchVoxelShapes", true);
            streamlineObjectHolderCleanup = builder
                    .comment(
                            "Refines redundant object-holder cleanup with early safety exits and",
                            "one override-owner snapshot per registry during the cleanup pass.",
                            "Requires the object-holder cleanup backport. Disable for its original",
                            "per-holder checks. Restart required; does not change removal eligibility.")
                    .define("streamlineObjectHolderCleanup", true);
            parallelReloadPreparation = builder
                    .comment(
                            "Uses an instrumented preparation barrier for resource reload listeners.",
                            "Minecraft 1.18.2 already overlaps listener preparation, so this mainly provides",
                            "visibility and a stable place for future reload scheduling improvements.",
                            "Disabled by default because it does not currently reduce server reload work.")
                    .define("parallelReloadPreparation", false);
            releaseMixinClassTrees = builder
                    .comment(
                            "Dedicated servers only: Mixin keeps a copy of every mixin's class tree",
                            "for the whole run (tens of MB in a large pack). Once the server has started, and again",
                            "five minutes later, the trees of mixins whose targets are all loaded are kept as",
                            "compressed class bytes instead and rebuilt if Mixin ever needs one again, so nothing",
                            "Mixin does changes. Needs exactly Mixin 0.8.5 with the reviewed field layout and no",
                            "hot-swap agent; otherwise nothing is touched. Default on after paired server and reload checks.",
                            "Disabled by Compare Mode and the common optimization master switch. Restart required.")
                    .define("releaseMixinClassTrees", true);
            skipRedundantRegistryValidation = builder
                    .comment(
                            "EXPERIMENTAL: emulates LaunchFaster's registry-validation skipping.",
                            "The original implementation skips two of every three global calls and can miss",
                            "validation for unrelated registries. It is implemented for parity but disabled",
                            "until profiling proves the work is material and a stage-aware replacement is ready.")
                    .define("skipRedundantRegistryValidation", false);
            skipRegistryDump = builder
                    .comment(
                            "Skips Forge registry dump calls.",
                            "Forge already avoids constructing dump tables unless REGISTRYDUMP debug logging",
                            "is enabled, so this normally changes nothing. Opt in only when that diagnostic",
                            "output is intentionally not needed.")
                    .define("skipRegistryDump", false);
            parallelBlockStateInit = builder
                    .comment(
                            "EXPERIMENTAL: initializes deferred BlockState caches concurrently.",
                            "Initializes deferred BlockState caches in parallel during block-registry bake.",
                            "Ignored when lazyBlockStateCache is enabled and disabled when ModernFix is present.")
                    .define("parallelBlockStateInit", false);
            lazyBlockStateCache = builder
                    .comment(
                            "EXPERIMENTAL: defers BlockState cache construction until selected accessors",
                            "need it. Disabled by default because modded blocks may read other cached fields.")
                    .define("lazyBlockStateCache", false);
            cacheResourceListing = builder
                    .comment("Caches repeated ResourceManager.listResources calls until the next reload.")
                    .define("cacheResourceListing", true);
            indexImmutableModResources = builder
                    .comment(
                            "Indexes immutable jar-backed mod resource packs as a lazy tree.",
                            "Repeated model, blockstate, texture, and existence queries",
                            "reuse the index. Folder packs, live generated packs, and failed",
                            "or suspicious scans always keep Forge's original path.")
                    .define("indexImmutableModResources", true);
            indexCraftTweakerRecipeRemovals = builder
                    .comment(
                            "CraftTweaker 9.1.213: indexes proven built-in output-removal cases.",
                            "Actions and removals stay sequential and in their original order.",
                            "Custom recipes, ingredients and ambiguous matching keep the original path.",
                            "Works on clients and dedicated servers. Restart required.")
                    .define("indexCraftTweakerRecipeRemovals", true);
            parallelKubeJsRecipeFilters = builder
                    .comment(
                            "KubeJS 1802.5.5-build.569: evaluates a recipe script's remove / forEachRecipe",
                            "filter across worker threads when it is built only from KubeJS's data-only",
                            "filters and ingredients, then applies the matches in the original order.",
                            "Script functions and custom predicates stay on the calling thread.",
                            "Clients and dedicated servers. Restart required.")
                    .define("parallelKubeJsRecipeFilters", true);
            indexKubeJsRecipeRemovals = builder
                    .comment(
                            "KubeJS 1802.5.5-build.569: answers a recipe script's remove / stage filter that",
                            "names one recipe id or one plain output item from a per-event lookup table",
                            "instead of scanning every recipe, then runs KubeJS's own filter on those",
                            "candidates in the original order. Regex, tag, mod, input, NBT and script",
                            "filters keep the normal scan. Clients and dedicated servers. Restart required.")
                    .define("indexKubeJsRecipeRemovals", true);
            asyncChunkDiskReads = builder
                    .comment(
                            "Reads chunk region data on the chunk IO thread before the server thread",
                            "deserializes it, instead of blocking the server thread on the disk read and",
                            "inflate for every loaded chunk (vanilla 1.19 made the same change).",
                            "Deserialization, POI updates, Forge's ChunkDataEvent.Load and every error",
                            "path still run on the server thread in the vanilla order.",
                            "Integrated and dedicated server. Restart required.")
                    .define("asyncChunkDiskReads", true);
            indexObjectHolderDispatch = builder
                    .comment(
                            "Forge applies object-holder lookups after every registry event by visiting every",
                            "holder in the pack (tens of thousands), nearly all of which belong to another",
                            "registry and return at once. This visits only the holders that can act for the",
                            "registry in question, in the same order, and leaves everything else to Forge.",
                            "Any holder or Forge layout it does not recognise keeps the original full pass.",
                            "Restart required. Disabled by Compare Mode and the common master switch.")
                    .define("indexObjectHolderDispatch", true);
            lazyBlockStatePropertyCodecs = builder
                    .comment(
                            "Dedicated servers. Vanilla builds a serialization codec for every block and fluid",
                            "(tens of thousands in a large pack, tens of megabytes of heap) though only block-state",
                            "(de)serialization reads it: chunk palettes, worldgen and datapack JSON. This builds",
                            "each one on first use instead, from the same code, so every encoded and decoded",
                            "value and error message is unchanged. The first save or load of a block type pays its",
                            "small build once. Applies only while no other mod's mixin has changed StateDefinition;",
                            "any other layout keeps the original eager codecs.",
                            "Restart required. Disabled by Compare Mode and the common master switch.")
                    .define("lazyBlockStatePropertyCodecs", true);
            presizeBlockStateIdMap = builder
                    .comment(
                            "Forge numbers every block state (about 1.5 million in a large Vault Hunters pack) in a",
                            "hash map and a list that start small and double repeatedly while it fills, re-hashing",
                            "every state each time. This sizes both once, before the first fill, from the number of",
                            "states, so every state gets the same id in the same order. It acts only on an empty",
                            "map of the exact reviewed layout, once per run; any other layout keeps Forge's fill.",
                            "Restart required. Disabled by Compare Mode and the common master switch.")
                    .define("presizeBlockStateIdMap", true);
            skipReturningPlayerSpawnSearch = builder
                    .comment(
                            "Skips the ServerPlayer constructor's world-spawn search (chunk loads and",
                            "collision probes around the shared spawn) when its result is discarded:",
                            "on login when saved player data exists, and on respawn when a valid",
                            "bed or anchor position was found. New players, players without a",
                            "respawn point, fake players and mod-constructed players keep the",
                            "vanilla search at its original point. Restart required.")
                    .define("skipReturningPlayerSpawnSearch", true);
            builder.pop();

            builder.push("backports");
            EnumMap<BackportFeature, ForgeConfigSpec.BooleanValue> options =
                    new EnumMap<>(BackportFeature.class);
            for (BackportFeature feature : BackportFeature.values()) {
                if (feature == BackportFeature.RESOURCE_PACK_INDEXING) {
                    options.put(feature, indexImmutableModResources);
                    continue;
                }
                if (feature == BackportFeature.EVENT_BUS_WRAPPER_CACHE_CONCURRENCY_FIX) {
                    options.put(feature, builder.comment(
                            "Repairs the reviewed EventBus 5.0.3 ASM wrapper cache before parallel mod construction.",
                            "Only wrapper creation takes a lock; event dispatch is unchanged.",
                            "Unknown or already-used layouts and denied Unsafe access keep original behavior.",
                            "Java 24+ may print its one-time Unsafe deprecation warning. Restart required.",
                            "Correctness fix: independent of Compare Mode and optimization masters.")
                            .define(feature.configKey(), feature.defaultEnabled()));
                    continue;
                }
                options.put(
                        feature,
                        builder.comment(
                                        feature.displayName() + ".",
                                        "Restart required. VH Accelerator only takes ownership",
                                        "when this option is enabled, the implementation is present,",
                                        "the physical side is compatible, Compare Mode is off, and",
                                        "VHA's compatibility and exact-option ownership checks select this implementation."
                                )
                                .define(
                                        feature.configKey(),
                                        feature.defaultEnabled()
                                )
                );
            }
            backports = Collections.unmodifiableMap(options);
            builder.pop();
        }
    }

    /**
     * Read during block registration; falls back to the default if the common
     * config is not loaded yet.
     */
    public static boolean decocraftModelTrimmingEnabled() {
        try {
            return !compareModeEnabled() && COMMON.trimDecocraftModels.get();
        } catch (IllegalStateException notLoaded) {
            return !compareModeEnabled();
        }
    }

    public static boolean buildingGadgetsTemplateCompactionEnabled() {
        try {
            return commonOptimizationsEnabled() && COMMON.compactBuildingGadgetsTemplates.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    /**
     * Read loaded values for each new distribution. Before Forge loads the spec, reuse the one
     * migrated bootstrap snapshot; existing distribution maps keep their original lifetime.
     */
    public static boolean optimizeVaultLootCdfEnabled() {
        if (compareModeEnabled()) return false;
        if (COMMON_SPEC.isLoaded()) {
            return COMMON.enableCommonOptimizations.get() && COMMON.optimizeVaultLootCdf.get();
        }
        return BootstrapCommonConfig.bool("optimizations", "enableCommonOptimizations", true)
                && BootstrapCommonConfig.bool("compatibility", "optimizeVaultLootCdf", true);
    }

    /** Read live for each new distribution; before Forge loads the spec, the migrated bootstrap snapshot. */
    public static boolean lazyCompactVaultLootCdfEnabled() {
        if (compareModeEnabled()) return false;
        if (COMMON_SPEC.isLoaded()) {
            return COMMON.enableCommonOptimizations.get() && COMMON.lazyCompactVaultLootCdf.get();
        }
        return BootstrapCommonConfig.bool("optimizations", "enableCommonOptimizations", true)
                && BootstrapCommonConfig.bool("compatibility", "lazyCompactVaultLootCdf", true);
    }

    /** Read when the server has started and once more five minutes later. */
    public static boolean releaseMixinClassTreesEnabled() {
        if (!COMMON_SPEC.isLoaded()) return false;
        try {
            return COMMON.releaseMixinClassTrees.get() && commonOptimizationsEnabled();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    public static boolean commonOptimizationsEnabled() {
        return !compareModeEnabled()
                && COMMON.enableCommonOptimizations.get();
    }

    public static boolean compareModeEnabled() {
        return BootstrapCompareMode.enabled();
    }

    public static void setCompareMode(boolean enabled) {
        COMMON.compareMode.set(enabled);
        COMMON.compareMode.save();
        BootstrapCompareMode.set(enabled);
        VHAccelerator.LOGGER.info(
                "Compare Mode {} and saved",
                enabled ? "enabled" : "disabled"
        );
    }

    public static boolean timersEnabled() {
        return COMMON.timers.get();
    }

    public static void setTimersEnabled(boolean enabled) {
        COMMON.timers.set(enabled);
        COMMON.timers.save();
    }

    public static boolean debugDiagnosticsEnabled() {
        return BootstrapDebugDiagnostics.enabled();
    }

    public static void setDebugDiagnosticsEnabled(boolean enabled) {
        COMMON.debug.set(enabled);
        COMMON.debug.save();
        BootstrapDebugDiagnostics.set(enabled);
    }

    public static boolean jeiRecipeAuditEnabled() {
        return COMMON.jeiRecipeAudit.get();
    }

    public static void setJeiRecipeAuditEnabled(boolean enabled) {
        COMMON.jeiRecipeAudit.set(enabled);
        COMMON.jeiRecipeAudit.save();
    }

    public static boolean startupJfrEnabled() {
        return COMMON.startupJfr.get();
    }

    public static boolean instrumentationEnabled() {
        return timersEnabled() || debugDiagnosticsEnabled();
    }
}
