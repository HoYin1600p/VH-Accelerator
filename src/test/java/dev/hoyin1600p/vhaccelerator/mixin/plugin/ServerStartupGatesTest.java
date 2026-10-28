package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;
import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import org.junit.jupiter.api.Test;

class ServerStartupGatesTest {
    private MixinGateRules rules(boolean client, boolean debug) {
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.physicalClient = client;
        return rules(facts, debug);
    }

    private MixinGateRules rules(GateFactsBuilder facts, boolean debug) {
        return rules(facts, debug, false);
    }

    private MixinGateRules rules(GateFactsBuilder facts, boolean debug, boolean supported) {
        return new MixinGateRules(facts.build(), new GateInputs() {
            public boolean startupAdapterSupported(String mixin) { return supported; }
            public boolean debugEnabled() { return debug; }
            public boolean vhaOwns(BackportFeature feature) { return false; }
            public boolean modernFixOptionEnabled(String option) { return false; }
            public boolean modernFixDynamicResourcesEnabled() { return false; }
            public void buildScapeInteropApplied() { }
            public void ctmMixinApplied() { }
            public void modernFixBakeDecision(boolean enabled) { }
        });
    }

    @Test
    void diagnosticAdaptersRequireDedicatedDebugKnownSupportedTargetsButNoOptimizationOwner() {
        String base = "dev.hoyin1600p.vhaccelerator.mixin.compat.startup.";
        for (boolean client : new boolean[]{false, true}) {
            for (boolean debug : new boolean[]{false, true}) {
                for (boolean supported : new boolean[]{false, true}) {
                    GateFactsBuilder facts = new GateFactsBuilder();
                    facts.physicalClient = client;
                    facts.modListKnown = true;
                    facts.modernFixLoaded = true;
                    facts.commonMasterEnabled = false;
                    for (String name : new String[]{"crafttweaker.CraftTweakerExecutionObserverMixin",
                            "crafttweaker.CraftTweakerActionObserverMixin", "kubejs.KubeScriptObserverMixin",
                            "kubejs.KubeRecipeObserverMixin", "selene.SeleneResourceObserverMixin"}) {
                        assertEquals(!client && debug && supported, rules(facts, debug, supported).shouldApply(base + name));
                        facts.modDiscoveryFailed = true;
                        assertFalse(rules(facts, debug, supported).shouldApply(base + name));
                        facts.modDiscoveryFailed = false;
                        facts.modListKnown = false;
                        assertFalse(rules(facts, debug, supported).shouldApply(base + name));
                        facts.modListKnown = true;
                    }
                    assertFalse(rules(facts, debug, supported).shouldApply(base + "selene.FutureMixin"));
                }
            }
        }
    }

    @Test
    void onlyAuditedCommonHooksCrossTheDedicatedCompatibilityGate() {
        String base = "dev.hoyin1600p.vhaccelerator.mixin.compat.";
        for (boolean debug : new boolean[]{false, true}) {
            for (boolean modernFix : new boolean[]{false, true}) {
                GateFactsBuilder facts = new GateFactsBuilder();
                facts.modListKnown = true;
                facts.commonMasterEnabled = true;
                facts.decocraftCompatible = true;
                facts.decocraftTrimmingEnabled = true;
                facts.ferriteCorePropertyMaps = true;
                facts.ferriteCoreLoaded = true;
                facts.modernFixLoaded = modernFix;
                for (String hook : new String[]{"decocraft.BBModelTextureDataMixin", "decocraft.JsonParserModelShareMixin",
                        "ferritecore.StateHolderPropertyMapMixin"}) {
                    assertTrue(rules(facts, debug).shouldApply(base + hook));
                    facts.commonMasterEnabled = false;
                    assertFalse(rules(facts, debug).shouldApply(base + hook));
                    facts.commonMasterEnabled = true;
                    facts.modListKnown = false;
                    assertFalse(rules(facts, debug).shouldApply(base + hook));
                    facts.modListKnown = true;
                    facts.modDiscoveryFailed = true;
                    assertFalse(rules(facts, debug).shouldApply(base + hook));
                    facts.modDiscoveryFailed = false;
                }
                facts.decocraftCompatible = false;
                assertFalse(rules(facts, debug).shouldApply(base + "decocraft.BBModelTextureDataMixin"));
                facts.decocraftCompatible = true;
                facts.decocraftTrimmingEnabled = false;
                assertFalse(rules(facts, debug).shouldApply(base + "decocraft.JsonParserModelShareMixin"));
                facts.ferriteCorePropertyMaps = false;
                assertFalse(rules(facts, debug).shouldApply(base + "ferritecore.StateHolderPropertyMapMixin"));
                assertFalse(rules(facts, debug).shouldApply(base + "decocraft.FutureMixin"));
                assertFalse(rules(facts, debug).shouldApply(base + "crafttweaker.RecipeListParallelRemovalMixin"));
                assertFalse(rules(facts, debug).shouldApply(base + "vaulthunters.TieredLootTableGeneratorCdfMixin"));
                facts.targetDummySetupFix = true;
                facts.vaultHuntersLoaded = true;
                facts.commonMasterEnabled = false;
                assertTrue(rules(facts, debug).shouldApply(base + "targetdummy.TargetDummySetupMixin"));
                assertTrue(rules(facts, debug).shouldApply(base + "vaulthunters.CrystalBuddingChunkLoadMixin"));
                facts.physicalClient = true;
                facts.decocraftCompatible = true;
                facts.ferriteCorePropertyMaps = true;
                assertTrue(rules(facts, debug).shouldApply(base + "decocraft.BBModelTextureDataMixin"));
                assertTrue(rules(facts, debug).shouldApply(base + "ferritecore.StateHolderPropertyMapMixin"));
            }
        }
    }

    @Test
    void theKubeJsRemovalIndexHasItsOwnSettingOnBothSides() {
        String index = "dev.hoyin1600p.vhaccelerator.mixin.compat.kubejs.RecipeEventJSRemoveIndexMixin";
        for (boolean debug : new boolean[]{false, true}) {
            GateFactsBuilder facts = new GateFactsBuilder();
            facts.modListKnown = true;
            facts.commonMasterEnabled = true;
            facts.indexKubeJsRecipeRemovals = true;
            // On: independent of the parallel-filter setting.
            facts.parallelKubeJsFilters = false;
            assertTrue(rules(facts, debug).shouldApply(index));
            facts.parallelKubeJsFilters = true;
            assertTrue(rules(facts, debug).shouldApply(index));
            // Off (config off, wrong KubeJS build or Compare Mode all clear this fact): original behaviour.
            facts.indexKubeJsRecipeRemovals = false;
            assertFalse(rules(facts, debug).shouldApply(index));
            facts.indexKubeJsRecipeRemovals = true;
            // A server also needs the common master and a known mod list.
            facts.commonMasterEnabled = false;
            assertFalse(rules(facts, debug).shouldApply(index));
            facts.commonMasterEnabled = true;
            facts.modListKnown = false;
            assertFalse(rules(facts, debug).shouldApply(index));
            facts.modListKnown = true;
            facts.modDiscoveryFailed = true;
            assertFalse(rules(facts, debug).shouldApply(index));
            facts.modDiscoveryFailed = false;
            // A client is decided by the setting alone.
            facts.physicalClient = true;
            facts.commonMasterEnabled = false;
            assertTrue(rules(facts, debug).shouldApply(index));
            facts.indexKubeJsRecipeRemovals = false;
            assertFalse(rules(facts, debug).shouldApply(index));
        }
    }

    @Test
    void onlyTheKubeJsRecipeFilterCrossesTheDedicatedGateAndNeedsEveryCondition() {
        String base = "dev.hoyin1600p.vhaccelerator.mixin.compat.kubejs.";
        String filter = base + "RecipeEventJSParallelFilterMixin";
        for (boolean debug : new boolean[]{false, true}) {
            GateFactsBuilder facts = new GateFactsBuilder();
            facts.modListKnown = true;
            facts.commonMasterEnabled = true;
            facts.parallelKubeJsFilters = true;
            assertTrue(rules(facts, debug).shouldApply(filter));
            facts.commonMasterEnabled = false;
            assertFalse(rules(facts, debug).shouldApply(filter));
            facts.commonMasterEnabled = true;
            facts.parallelKubeJsFilters = false;
            assertFalse(rules(facts, debug).shouldApply(filter));
            facts.parallelKubeJsFilters = true;
            facts.modListKnown = false;
            assertFalse(rules(facts, debug).shouldApply(filter));
            facts.modListKnown = true;
            facts.modDiscoveryFailed = true;
            assertFalse(rules(facts, debug).shouldApply(filter));
            facts.modDiscoveryFailed = false;
            // The resource-pack listing, the JEI hook and any future member stay client-only.
            for (String other : new String[]{"HideJEIEventJSMixin", "KubeJSResourcePackExistsMixin", "FutureMixin"}) {
                assertFalse(rules(facts, debug).shouldApply(base + other), other);
            }
            // A client keeps its former rule: the setting alone decides, as before.
            facts.physicalClient = true;
            facts.commonMasterEnabled = false;
            assertTrue(rules(facts, debug).shouldApply(filter));
            facts.parallelKubeJsFilters = false;
            assertFalse(rules(facts, debug).shouldApply(filter));
        }
    }

    @Test
    void dedicatedTotalsRemainIndependentOfDetailedDiagnosticsAndOptionalMods() {
        for (boolean client : new boolean[]{true, false}) {
            for (boolean debug : new boolean[]{true, false}) {
                MixinGateRules rules = rules(client, debug);
                assertEquals(!client, rules.shouldApply("test.ServerMainMixin"));
                assertEquals(!client, rules.shouldApply("test.DedicatedServerStartupMixin"));
                assertEquals(!client, rules.shouldApply("test.ServerLifecycleStartupMixin"));
                assertEquals(!client && debug, rules.shouldApply("test.ServerWorldStartupProfilerMixin"));
                assertEquals(!client && debug, rules.shouldApply("test.ServerModLoaderStartupMixin"));
                assertFalse(rules(false, debug).shouldApply("test.mixin.compat.unknown.FutureMixin"));
            }
        }
    }
}
