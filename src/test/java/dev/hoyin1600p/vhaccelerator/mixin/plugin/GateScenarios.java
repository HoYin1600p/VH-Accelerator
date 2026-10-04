package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;

/**
 * The environment states the mixin gating regression test covers, and the
 * encoding that turns a gating decision for every configured mixin into a
 * short fingerprint. Facts are keyed by the plugin's historical field names.
 */
final class GateScenarios {
    static final List<String> FLAGS = List.of(
            "modernFixLoaded", "modDiscoveryFailed", "modListKnown",
            "ferriteCoreLoaded", "externalShapeOptimizerLoaded",
            "optifinePresent", "jeiLoaded", "vaultHuntersLoaded", "powahLoaded",
            "jeiTweakerLoaded", "jerLoaded", "craftTweakerLoaded",
            "thermalLoaded", "ironFurnacesLoaded", "industrialForegoingLoaded",
            "ae2Loaded", "elevatorLoaded", "extraStorageLoaded",
            "refinedStorageLoaded", "sophisticatedCoreLoaded",
            "supplementariesLoaded", "ctmCompatible", "ctmInstalled",
            "buildScapeInstalled", "mekanismModelBakeCompatible",
            "cableTiersModelBakeCompatible", "cloudStorageModelBakeCompatible",
            "megaCellsModelBakeCompatible", "everyCompatDebugDumpCompatible",
            "decocraftCompatible", "ferriteCorePropertyMaps",
            "renderOptimizationLoaded", "releaseLevelPinningReferences",
            "copycatsLoaded", "parallelKubeJsFilters", "asyncChunkDiskReads",
            "create051i", "geckoLib3057", "arsNouveauGeckoLib",
            "everyCompatPackCache", "vaultCascadeScan",
            "xaeroMinimapCompatible", "xaeroWorldMapCompatible",
            "farsightLoaded", "vroOwnsFarsightBound", "physicalClient",
            "targetDummySetupFix", "smoothBootPriorityRestore",
            "skipReturningPlayerSpawnSearch"
    );

    /** How the scripted ModernFix plugin answers option queries. */
    enum ModernFixMode {
        NOT_INSTALLED, QUERY_FAILS, ALL_OFF, DYNAMIC_ON_REST_OFF, ALL_ON, DYNAMIC_OFF_REST_ON;

        void install() {
            switch (this) {
                case NOT_INSTALLED -> ModernFixMixinPlugin.uninstall();
                case QUERY_FAILS -> ModernFixMixinPlugin.install(Map.of(), null);
                case ALL_OFF -> ModernFixMixinPlugin.install(Map.of(), false);
                case DYNAMIC_ON_REST_OFF -> ModernFixMixinPlugin.install(
                        Map.of("perf.dynamic_resources.ModelBakeryMixin", true), false);
                case ALL_ON -> ModernFixMixinPlugin.install(Map.of(), true);
                case DYNAMIC_OFF_REST_ON -> ModernFixMixinPlugin.install(
                        Map.of("perf.dynamic_resources.ModelBakeryMixin", false), true);
            }
        }
    }

    /** Process-wide inputs that the gating decision reads outside the mod list. */
    record Statics(boolean debug, Set<BackportFeature> owned, ModernFixMode modernFix) {
        String label() {
            String owns = owned.isEmpty() ? "own-none"
                    : owned.size() == BackportFeature.values().length ? "own-all"
                    : "own-" + owned.iterator().next().id();
            return (debug ? "debug" : "nodebug") + "/" + owns + "/" + modernFix;
        }
    }

    record Facts(String name, Map<String, Object> values) {
    }

    private GateScenarios() {
    }

    static List<Facts> factVariants() {
        List<Facts> variants = new ArrayList<>();
        Map<String, Object> allFalse = baseline(false);
        Map<String, Object> allTrue = baseline(true);
        variants.add(new Facts("all-false", allFalse));
        variants.add(new Facts("all-true", allTrue));
        for (String flag : FLAGS) {
            variants.add(new Facts("false+" + flag, with(allFalse, flag, true)));
            variants.add(new Facts("true-" + flag, with(allTrue, flag, false)));
        }
        for (int generation : new int[] {9, 10}) {
            variants.add(new Facts("jei" + generation, with(
                    with(allFalse, "jeiLoaded", true), "jeiGeneration", generation)));
            variants.add(new Facts("all-true-jei" + generation,
                    with(allTrue, "jeiGeneration", generation)));
            variants.add(new Facts("client-mf-jei" + generation, with(with(with(with(
                    allFalse, "physicalClient", true), "modernFixLoaded", true),
                    "jeiLoaded", true), "jeiGeneration", generation)));
        }
        variants.add(new Facts("dedicated-server", with(with(allFalse, "modListKnown", true),
                "asyncChunkDiskReads", true)));
        variants.add(new Facts("client-vanilla", with(with(allFalse, "physicalClient", true),
                "modListKnown", true)));
        variants.add(new Facts("client-modernfix", with(with(with(allFalse, "physicalClient", true),
                "modListKnown", true), "modernFixLoaded", true)));
        variants.add(new Facts("client-modernfix-ferritecore", with(with(with(with(allFalse,
                "physicalClient", true), "modListKnown", true), "modernFixLoaded", true),
                "ferriteCoreLoaded", true)));
        variants.add(new Facts("client-buildscape", with(with(with(allFalse, "physicalClient", true),
                "modListKnown", true), "buildScapeInstalled", true)));
        variants.add(new Facts("client-ctm-exact", with(with(with(with(allFalse, "physicalClient", true),
                "modListKnown", true), "ctmInstalled", true), "ctmCompatible", true)));
        variants.add(new Facts("client-ctm-unsupported", with(with(with(allFalse, "physicalClient", true),
                "modListKnown", true), "ctmInstalled", true)));
        variants.add(new Facts("client-discovery-failed", with(with(allFalse, "physicalClient", true),
                "modDiscoveryFailed", true)));
        return variants;
    }

    static List<Statics> staticVariants() {
        List<Statics> variants = new ArrayList<>();
        Set<BackportFeature> all = EnumSet.allOf(BackportFeature.class);
        Set<BackportFeature> none = EnumSet.noneOf(BackportFeature.class);
        for (boolean debug : new boolean[] {false, true}) {
            for (Set<BackportFeature> owned : List.of(none, all)) {
                for (ModernFixMode mode : ModernFixMode.values()) {
                    variants.add(new Statics(debug, owned, mode));
                }
            }
        }
        return variants;
    }

    /** Each feature owned alone, with ModernFix absent. */
    static List<Statics> singleOwnershipVariants() {
        List<Statics> variants = new ArrayList<>();
        for (BackportFeature feature : BackportFeature.values()) {
            variants.add(new Statics(false, EnumSet.of(feature), ModernFixMode.NOT_INSTALLED));
        }
        return variants;
    }

    /** Fact sets that the single-ownership variants run against. */
    static List<Facts> singleOwnershipFacts() {
        Map<String, Object> allTrue = baseline(true);
        Map<String, Object> allFalse = baseline(false);
        Map<String, Object> client = with(with(allFalse, "physicalClient", true), "modListKnown", true);
        Map<String, Object> jei10 = with(with(allTrue, "jeiGeneration", 10), "optifinePresent", false);
        return List.of(
                new Facts("all-true-jei10", jei10),
                new Facts("all-true-jei9", with(allTrue, "jeiGeneration", 9)),
                new Facts("all-false", allFalse),
                new Facts("client-vanilla", client));
    }

    /** Every configured mixin plus synthetic names that reach the fall-through rules. */
    static List<String> mixinNames() {
        List<String> names = new ArrayList<>(CompatMixinGroups.readConfiguredMixins(
                GateScenarios.class.getClassLoader(), "vhaccelerator.mixins.json"));
        String base = "dev.hoyin1600p.vhaccelerator.mixin.";
        for (String synthetic : List.of(
                "Unlisted", "client.Unlisted", "compat.jei.Unlisted", "compat.jei.v9.Unlisted",
                "compat.jei.v10.Unlisted", "client.compat.Unlisted", "backport.modernfix.Unlisted")) {
            names.add(base + synthetic);
        }
        return names;
    }

    static Map<String, Object> baseline(boolean value) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String flag : FLAGS) {
            values.put(flag, value);
        }
        values.put("jeiGeneration", 0);
        return values;
    }

    private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(base);
        copy.put(key, value);
        return copy;
    }

    /** Short digest of the per-mixin decision string for one scenario. */
    static String fingerprint(String encodedDecisions) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(encodedDecisions.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
