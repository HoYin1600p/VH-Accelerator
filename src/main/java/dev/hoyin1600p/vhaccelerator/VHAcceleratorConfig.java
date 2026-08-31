package dev.hoyin1600p.vhaccelerator;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
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
        public final ForgeConfigSpec.BooleanValue enableCommonOptimizations;
        public final ForgeConfigSpec.BooleanValue parallelReloadPreparation;
        public final ForgeConfigSpec.BooleanValue skipRedundantRegistryValidation;
        public final ForgeConfigSpec.BooleanValue skipRegistryDump;
        public final ForgeConfigSpec.BooleanValue parallelBlockStateInit;
        public final ForgeConfigSpec.BooleanValue lazyBlockStateCache;
        public final ForgeConfigSpec.BooleanValue cacheResourceListing;
        public final ForgeConfigSpec.BooleanValue indexImmutableModResources;
        public final Map<BackportFeature, ForgeConfigSpec.BooleanValue> backports;

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
                            "Indexes immutable jar-backed mod resource packs as a lazy tree.",
                            "Repeated model, blockstate, texture, and existence queries",
                            "reuse the index. Folder packs, live generated packs, and failed",
                            "or suspicious scans always keep Forge's original path.")
                    .define("indexImmutableModResources", true);
            builder.pop();

            builder.push("backports");
            EnumMap<BackportFeature, ForgeConfigSpec.BooleanValue> options =
                    new EnumMap<>(BackportFeature.class);
            for (BackportFeature feature : BackportFeature.values()) {
                if (feature == BackportFeature.RESOURCE_PACK_INDEXING) {
                    options.put(feature, indexImmutableModResources);
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

    public static boolean instrumentationEnabled() {
        return timersEnabled() || debugDiagnosticsEnabled();
    }
}
