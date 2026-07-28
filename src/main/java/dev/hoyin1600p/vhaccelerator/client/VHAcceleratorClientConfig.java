package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class VHAcceleratorClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Values VALUES;

    static {
        Pair<Values, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Values::new);
        VALUES = pair.getLeft();
        SPEC = pair.getRight();
    }

    private VHAcceleratorClientConfig() {
    }

    public static boolean optimizationsEnabled() {
        return !VHAcceleratorConfig.compareModeEnabled()
                && VALUES.enableClientOptimizations.get();
    }

    public static final class Values {
        public final ForgeConfigSpec.BooleanValue enableClientOptimizations;
        public final ForgeConfigSpec.BooleanValue parallelModelLoading;
        public final ForgeConfigSpec.BooleanValue parallelAtlasStitching;
        public final ForgeConfigSpec.BooleanValue parallelModelBaking;
        public final ForgeConfigSpec.BooleanValue persistentModelJsonCache;
        public final ForgeConfigSpec.BooleanValue asyncUserApiService;
        public final ForgeConfigSpec.BooleanValue memoizeModelMaterials;
        public final ForgeConfigSpec.BooleanValue protectDynamicModels;
        public final ForgeConfigSpec.BooleanValue parallelJeiIngredientSorting;
        public final ForgeConfigSpec.BooleanValue indexPowahWikiRecipes;
        public final ForgeConfigSpec.BooleanValue parallelJeiTweakerMatching;
        public final ForgeConfigSpec.IntValue jeiTweakerParallelThreshold;
        public final ForgeConfigSpec.BooleanValue stagedVaultGroupLoading;
        public final ForgeConfigSpec.IntValue vaultGroupTickBudgetMillis;
        public final ForgeConfigSpec.BooleanValue asyncJeiSearchIndex;
        public final ForgeConfigSpec.BooleanValue parallelJeiSearchPrefixes;
        public final ForgeConfigSpec.BooleanValue optimizeJeiIngredientFilterConstruction;
        public final ForgeConfigSpec.BooleanValue persistentVanillaIngredientCache;
        public final ForgeConfigSpec.BooleanValue parallelVanillaRecipeValidation;
        public final ForgeConfigSpec.BooleanValue persistentVanillaRecipeValidationCache;
        public final ForgeConfigSpec.BooleanValue persistentJeiRecipeIndexCache;
        public final ForgeConfigSpec.BooleanValue cacheJerCompatibility;
        public final ForgeConfigSpec.BooleanValue parallelCraftTweakerTagBinding;
        public final ForgeConfigSpec.BooleanValue compactCraftTweakerClientReplayLogging;
        public final ForgeConfigSpec.BooleanValue parallelThermalRecipeRefresh;
        public final ForgeConfigSpec.BooleanValue cacheIronFurnacesJeiRecipes;
        public final ForgeConfigSpec.BooleanValue persistentIronFurnacesFuelCache;
        public final ForgeConfigSpec.BooleanValue precompileIronFurnacesJeiRecipes;
        public final ForgeConfigSpec.IntValue ironFurnacesPrecompileFrameBudgetMillis;
        public final ForgeConfigSpec.BooleanValue optimizeIndustrialForegoingStoneWorkJeiRecipes;
        public final ForgeConfigSpec.BooleanValue cacheVaultTooltips;
        public final ForgeConfigSpec.BooleanValue optimizeVaultAtlasValidation;
        public final ForgeConfigSpec.BooleanValue profileClientLaunchPhases;
        public final ForgeConfigSpec.BooleanValue showLaunchTimer;

        private Values(ForgeConfigSpec.Builder builder) {
            builder.push("optimizations");
            enableClientOptimizations = builder
                    .comment("Master switch for optimizations that only apply to the physical client.")
                    .define("enableClientOptimizations", true);
            parallelModelLoading = builder
                    .comment(
                            "Reads and parses plain model JSON concurrently.",
                            "Forge custom loaders, BuildScape models, cache misses, and parse failures",
                            "remain on their established loading paths.")
                    .define("parallelModelLoading", true);
            parallelAtlasStitching = builder
                    .comment("Prepares independent texture atlases concurrently.")
                    .define("parallelAtlasStitching", true);
            parallelModelBaking = builder
                    .comment("Bakes top-level models in batches on Minecraft's background executor.")
                    .define("parallelModelBaking", true);
            persistentModelJsonCache = builder
                    .comment(
                            "Persists the resolved initial model JSON resource view as one",
                            "checksummed file and restores it only when the mod files,",
                            "resource packs, relevant local configs, and pack order match.",
                            "Forge still parses every JSON and runs every custom geometry loader;",
                            "parsed models, baked models, textures, and dynamic state are never stored.",
                            "Runtime resource reloads always bypass this cache.")
                    .define("persistentModelJsonCache", true);
            asyncUserApiService = builder
                    .comment(
                            "Creates the online UserApiService asynchronously.",
                            "Unlike LaunchFaster, the completed service is retained behind a non-blocking proxy.")
                    .define("asyncUserApiService", true);
            memoizeModelMaterials = builder
                    .comment("Memoizes BlockModel material dependency walks for each model instance.")
                    .define("memoizeModelMaterials", true);
            builder.pop();

            builder.push("compatibility");
            protectDynamicModels = builder
                    .comment(
                            "Keeps Forge custom geometry, dynamic models, and their dependency graphs",
                            "on vanilla's single-threaded atlas and model paths.",
                            "Also retries every model sequentially if a parallel bake fails.",
                            "Keep this enabled unless diagnosing the compatibility guard itself.")
                    .define("protectDynamicModels", true);
            parallelJeiIngredientSorting = builder
                    .comment(
                            "Uses a dedicated adaptive worker pool for JEI's ingredient pre-sort.",
                            "Worker count is derived from available processors and reduced after",
                            "the first playable frame so the sort does not saturate gameplay.",
                            "The sort remains synchronous: JEI is not published and plugins are not notified",
                            "until every sorted index has been assigned.")
                    .define("parallelJeiIngredientSorting", true);
            indexPowahWikiRecipes = builder
                    .comment(
                            "Indexes crafting and smelting recipes once for Powah's in-game wiki.",
                            "This replaces repeated full recipe-list scans after each server connection.")
                    .define("indexPowahWikiRecipes", true);
            parallelJeiTweakerMatching = builder
                    .comment(
                            "Matches JEITweaker hidden ingredients against a stable snapshot in parallel.",
                            "Runtime removals remain synchronous and failures retry with the original",
                            "single-threaded matching behavior.")
                    .define("parallelJeiTweakerMatching", true);
            jeiTweakerParallelThreshold = builder
                    .comment("Minimum JEI ingredient count before hidden matching uses the bounded worker pool.")
                    .defineInRange("jeiTweakerParallelThreshold", 256, 32, 100000);
            stagedVaultGroupLoading = builder
                    .comment(
                            "Builds Vault block and entity groups in bounded main-thread slices.",
                            "Only complete group maps are published; no worker ever touches live entities.")
                    .define("stagedVaultGroupLoading", true);
            vaultGroupTickBudgetMillis = builder
                    .comment("Maximum main-thread time used by staged Vault group loading per client tick.")
                    .defineInRange("vaultGroupTickBudgetMillis", 4, 1, 25);
            asyncJeiSearchIndex = builder
                    .comment(
                            "Builds JEI's ingredient search index as a private worker-owned object.",
                            "Plugin registration and JEI lifecycle work stay on Minecraft's main thread.",
                            "The complete index is published with one main-thread reference swap.",
                            "Runtime ingredient additions are journaled and merged before publication.",
                            "A failed worker build automatically retries on the main thread.")
                    .define("asyncJeiSearchIndex", true);
            parallelJeiSearchPrefixes = builder
                    .comment(
                            "Populates each independent JEI search-prefix storage in parallel.",
                            "Ingredient order inside every prefix remains sequential and the private",
                            "index is published only after all prefixes complete.",
                            "Any failure discards the private index and uses the sequential fallback.")
                    .define("parallelJeiSearchPrefixes", true);
            optimizeJeiIngredientFilterConstruction = builder
                    .comment(
                            "Avoids repeated UID and cache-invalidation work while JEI constructs",
                            "its initial ingredient filter. The UID shortcut is used only for",
                            "item stacks when both JEI blacklist sets are empty; otherwise JEI's",
                            "original visibility checks run unchanged.")
                    .define("optimizeJeiIngredientFilterConstruction", true);
            persistentVanillaIngredientCache = builder
                    .comment(
                            "Persists JEI's completed vanilla item ingredient list between launches.",
                            "A cached list is restored only when the server address, JEI generation,",
                            "installed mods/files, item registry, registered client/common configs,",
                            "synchronized item tags, and Forge server configs match.",
                            "A miss runs JEI's original factory.",
                            "Item stacks are reconstructed on the client thread before JEI sees them.")
                    .define("persistentVanillaIngredientCache", true);
            parallelVanillaRecipeValidation = builder
                    .comment(
                            "Validates JEI's vanilla crafting, furnace, smoking, blasting,",
                            "campfire, stonecutting, and smithing recipe lists in a bounded pool.",
                            "Result ordering is preserved and any failure retries JEI's original",
                            "sequential validation path.")
                    .define("parallelVanillaRecipeValidation", true);
            persistentVanillaRecipeValidationCache = builder
                    .comment(
                            "Persists the IDs that passed JEI's vanilla recipe validation.",
                            "Disabled by default because fingerprinting a large synchronized",
                            "recipe set can cost more than parallel validation saves.",
                            "Recipe objects are always resolved from the active world; cached IDs",
                            "are accepted only when the server recipe/tag payloads, synchronized",
                            "server configs, and installed mod files match.",
                            "Volatile UI and per-world client settings are intentionally excluded",
                            "because they do not define the server-synchronized recipe set.")
                    .define("persistentVanillaRecipeValidationCache", false);
            persistentJeiRecipeIndexCache = builder
                    .comment(
                            "Persists JEI's deterministic vanilla recipe-to-ingredient index.",
                            "Cached string plans are restored only when the exact recipe payload,",
                            "item tags, server and local configs, installed mod files, server,",
                            "and JEI generation match. Recipe objects always come from the active world.",
                            "A miss builds JEI's original layouts before recording the result.")
                    .define("persistentJeiRecipeIndexCache", true);
            cacheJerCompatibility = builder
                    .comment(
                            "Preloads Just Enough Resources' local loot tables asynchronously in menus,",
                            "then reuses its compatibility scan for later JEI rebuilds.",
                            "Connecting before the preload finishes safely waits for the remaining work.",
                            "JER data is local to the installed pack, so cluster transfers do not rebuild it.")
                    .define("cacheJerCompatibility", true);
            parallelCraftTweakerTagBinding = builder
                    .comment(
                            "Decodes CraftTweaker's independent synchronized tag view in parallel.",
                            "Each registry is built in worker-owned memory, all workers are joined",
                            "before publication, and a failure retries CraftTweaker's original path.")
                    .define("parallelCraftTweakerTagBinding", true);
            compactCraftTweakerClientReplayLogging = builder
                    .comment(
                            "Compacts repeated per-action INFO messages while CraftTweaker",
                            "replays server-synchronized scripts on the client.",
                            "Warnings, errors, script file names, and lifecycle messages remain.",
                            "Disable this when individual client replay actions are needed",
                            "in crafttweaker.log for script troubleshooting.")
                    .define("compactCraftTweakerClientReplayLogging", true);
            parallelThermalRecipeRefresh = builder
                    .comment(
                            "Refreshes independent Thermal machine recipe managers concurrently.",
                            "Defers Stirling's duplicate pre-tag furnace-fuel scan and restores",
                            "validated base fuel values from disk when server tags/configs match.",
                            "Thermal's explicit recipe overrides are always applied fresh.",
                            "The Forge recipe/tag event remains blocked until every manager finishes,",
                            "so no incomplete Thermal recipe state reaches the first world frame.")
                    .define("parallelThermalRecipeRefresh", true);
            cacheIronFurnacesJeiRecipes = builder
                    .comment(
                            "Builds Iron Furnaces' fuel and smoking JEI lists in one main-thread pass",
                            "and reuses the immutable lists for later JEI rebuilds in this game session.",
                            "Actual generator recipes remain world-specific and are always read fresh.")
                    .define("cacheIronFurnacesJeiRecipes", true);
            persistentIronFurnacesFuelCache = builder
                    .comment(
                            "Persists validated Iron Furnaces fuel results between client launches.",
                            "A cached list is used only when synchronized item tags and Forge configs,",
                            "local mod versions/files, and the item registry match.",
                            "Recipes and unrelated client configs do not affect furnace fuel values.",
                            "Cache misses rebuild before the first world frame; no fuel scan is deferred.")
                    .define("persistentIronFurnacesFuelCache", true);
            precompileIronFurnacesJeiRecipes = builder
                    .comment(
                            "Precompiles only Iron Furnaces' server-independent smoking list",
                            "in small menu-frame slices. Fuel burn times are always evaluated",
                            "after the active client world exists so server tags and config apply.")
                    .define("precompileIronFurnacesJeiRecipes", true);
            ironFurnacesPrecompileFrameBudgetMillis = builder
                    .comment(
                            "Maximum main-thread time used by the smoking-list precompile per menu frame.",
                            "The default targets smooth menus while normally completing during server selection.")
                    .defineInRange("ironFurnacesPrecompileFrameBudgetMillis", 3, 1, 8);
            optimizeIndustrialForegoingStoneWorkJeiRecipes = builder
                    .comment(
                            "Builds Industrial Foregoing stonework combinations incrementally",
                            "and gives JEI only the shortest equivalent path for each output.",
                            "Results remain based on the active server recipe manager.")
                    .define("optimizeIndustrialForegoingStoneWorkJeiRecipes", true);
            cacheVaultTooltips = builder
                    .comment(
                            "Caches Vault Hunters tooltip lookups by item and active locale.",
                            "Only used when both the client optimization master switch and The Vault are present.")
                    .define("cacheVaultTooltips", true);
            optimizeVaultAtlasValidation = builder
                    .comment(
                            "Validates Vault texture atlases with constant-time membership checks.",
                            "The same missing and unused textures are counted, but warning details",
                            "are bounded so a large mismatch cannot stall launch with log output.",
                            "This changes diagnostics only; atlas contents are never modified.")
                    .define("optimizeVaultAtlasValidation", true);
            builder.pop();

            builder.push("display");
            profileClientLaunchPhases = builder
                    .comment(
                            "Profiles the initial client resource reload and logs prepare/apply",
                            "timings for listeners that take at least 20 milliseconds.",
                            "The profiler observes the existing futures and does not change",
                            "listener order, executors, or menu precompile behavior.")
                    .define("profileClientLaunchPhases", true);
            showLaunchTimer = builder
                    .comment(
                            "Shows measured launch time on the title screen and after joining a world.",
                            "Multiplayer joins also show connect-to-first-playable-frame server login time.",
                            "The JEI search-index worker reports remaining post-login work",
                            "when its completed index is published.")
                    .define("showLaunchTimer", true);
            builder.pop();
        }
    }
}
