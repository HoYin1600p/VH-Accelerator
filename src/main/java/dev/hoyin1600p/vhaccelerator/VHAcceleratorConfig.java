package dev.hoyin1600p.vhaccelerator;

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
        public final ForgeConfigSpec.BooleanValue enableCommonOptimizations;
        public final ForgeConfigSpec.BooleanValue parallelReloadPreparation;
        public final ForgeConfigSpec.BooleanValue skipRedundantRegistryValidation;
        public final ForgeConfigSpec.BooleanValue skipRegistryDump;
        public final ForgeConfigSpec.BooleanValue parallelBlockStateInit;
        public final ForgeConfigSpec.BooleanValue lazyBlockStateCache;
        public final ForgeConfigSpec.BooleanValue cacheResourceListing;
        public final ForgeConfigSpec.BooleanValue indexImmutableModResources;

        private Common(ForgeConfigSpec.Builder builder) {
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
                            "Shows launch, login, transfer, post-login, and disconnect",
                            "measurements in the UI and writes their routine timing summaries",
                            "to the log. The /vha timers command changes this setting.",
                            "Internal lifecycle timestamps required for safe optimizations",
                            "remain available when this display setting is disabled.")
                    .define("timers", false);
            debug = builder
                    .comment(
                            "Enables detailed launch, reload, model, connection, packet,",
                            "and disconnect diagnostics. This adds measurement and logging",
                            "overhead and is disabled by default for normal play.",
                            "The /vha debug command changes this setting.")
                    .define("debug", false);
            builder.pop();

            builder.push("optimizations");
            enableCommonOptimizations = builder
                    .comment("Master switch for optimizations that are safe on both client and dedicated server.")
                    .define("enableCommonOptimizations", true);
            parallelReloadPreparation = builder
                    .comment(
                            "Uses an instrumented preparation barrier for resource reload listeners.",
                            "Minecraft 1.18.2 already overlaps listener preparation, so this mainly provides",
                            "visibility and a stable place for future reload scheduling improvements.",
                            "Disabled by default because it does not currently reduce server reload work.")
                    .define("parallelReloadPreparation", false);
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
                            "Indexes each immutable jar-backed mod resource namespace once.",
                            "Repeated model, blockstate, texture, and existence queries",
                            "reuse the index. Folder packs, live generated packs, empty-prefix",
                            "queries, failed scans, and ModernFix always keep their original path.")
                    .define("indexImmutableModResources", true);
            builder.pop();
        }
    }

    public static boolean commonOptimizationsEnabled() {
        return !compareModeEnabled()
                && COMMON.enableCommonOptimizations.get();
    }

    public static boolean compareModeEnabled() {
        return COMMON.compareMode.get();
    }

    public static void setCompareMode(boolean enabled) {
        COMMON.compareMode.set(enabled);
        COMMON.compareMode.save();
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
        return COMMON.debug.get();
    }

    public static void setDebugDiagnosticsEnabled(boolean enabled) {
        COMMON.debug.set(enabled);
        COMMON.debug.save();
    }

    public static boolean instrumentationEnabled() {
        return timersEnabled() || debugDiagnosticsEnabled();
    }
}
