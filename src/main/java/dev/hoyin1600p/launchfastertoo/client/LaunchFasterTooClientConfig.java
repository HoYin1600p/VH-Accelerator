package dev.hoyin1600p.launchfastertoo.client;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class LaunchFasterTooClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Values VALUES;

    static {
        Pair<Values, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Values::new);
        VALUES = pair.getLeft();
        SPEC = pair.getRight();
    }

    private LaunchFasterTooClientConfig() {
    }

    public static final class Values {
        public final ForgeConfigSpec.BooleanValue enableClientOptimizations;
        public final ForgeConfigSpec.BooleanValue parallelModelLoading;
        public final ForgeConfigSpec.BooleanValue parallelAtlasStitching;
        public final ForgeConfigSpec.BooleanValue parallelModelBaking;
        public final ForgeConfigSpec.BooleanValue asyncUserApiService;
        public final ForgeConfigSpec.BooleanValue memoizeModelMaterials;
        public final ForgeConfigSpec.BooleanValue protectDynamicModels;
        public final ForgeConfigSpec.BooleanValue parallelJeiIngredientSorting;
        public final ForgeConfigSpec.BooleanValue indexPowahWikiRecipes;
        public final ForgeConfigSpec.BooleanValue parallelJeiTweakerMatching;
        public final ForgeConfigSpec.IntValue jeiTweakerParallelThreshold;
        public final ForgeConfigSpec.BooleanValue stagedVaultGroupLoading;
        public final ForgeConfigSpec.IntValue vaultGroupTickBudgetMillis;
        public final ForgeConfigSpec.BooleanValue asyncJeiStartup;
        public final ForgeConfigSpec.BooleanValue cacheVaultTooltips;
        public final ForgeConfigSpec.BooleanValue showLaunchTimer;

        private Values(ForgeConfigSpec.Builder builder) {
            builder.push("optimizations");
            enableClientOptimizations = builder
                    .comment("Master switch for optimizations that only apply to the physical client.")
                    .define("enableClientOptimizations", true);
            parallelModelLoading = builder
                    .comment("Reads model JSON resources concurrently before ModelBakery parses them.")
                    .define("parallelModelLoading", true);
            parallelAtlasStitching = builder
                    .comment("Prepares independent texture atlases concurrently.")
                    .define("parallelAtlasStitching", true);
            parallelModelBaking = builder
                    .comment("Bakes top-level models in batches on Minecraft's background executor.")
                    .define("parallelModelBaking", true);
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
                            "Uses a parallel stream for JEI's ingredient pre-sort.",
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
            asyncJeiStartup = builder
                    .comment(
                            "EXPERIMENTAL: prepares JEI on a single cancellable worker during world entry.",
                            "Global runtime publication, plugin runtime callbacks, and event registration",
                            "are deferred to the main thread. Stale server generations cannot publish.",
                            "Disabled by default until the exact mod list has completed cluster testing.")
                    .define("asyncJeiStartup", false);
            cacheVaultTooltips = builder
                    .comment(
                            "Caches Vault Hunters tooltip lookups by item and active locale.",
                            "Only used when both the client optimization master switch and The Vault are present.")
                    .define("cacheVaultTooltips", true);
            builder.pop();

            builder.push("display");
            showLaunchTimer = builder
                    .comment(
                            "Shows measured launch time on the title screen and after joining a world.",
                            "Multiplayer joins also show connect-to-first-playable-frame server login time.")
                    .define("showLaunchTimer", true);
            builder.pop();
        }
    }
}
