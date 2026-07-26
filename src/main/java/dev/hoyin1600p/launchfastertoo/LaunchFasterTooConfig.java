package dev.hoyin1600p.launchfastertoo;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class LaunchFasterTooConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    private LaunchFasterTooConfig() {
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue enableCommonOptimizations;
        public final ForgeConfigSpec.BooleanValue parallelReloadPreparation;
        public final ForgeConfigSpec.BooleanValue skipRedundantRegistryValidation;
        public final ForgeConfigSpec.BooleanValue skipRegistryDump;
        public final ForgeConfigSpec.BooleanValue parallelBlockStateInit;
        public final ForgeConfigSpec.BooleanValue lazyBlockStateCache;
        public final ForgeConfigSpec.BooleanValue cacheResourceListing;

        private Common(ForgeConfigSpec.Builder builder) {
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
            builder.pop();
        }
    }
}
