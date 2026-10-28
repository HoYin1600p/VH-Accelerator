package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;
import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class CraftTweakerRemovalGateTest {
    private static final String BASE = "dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker.";
    private static final List<String> HOOKS = List.of("ScriptRunIndexSessionMixin",
            "ScriptRunManagerOutputContextMixin", "RecipeListIndexedOutputMixin", "AccessibleRecipeMapsMixin");

    private static MixinGateRules rules(GateFactsBuilder facts, boolean debug) {
        return new MixinGateRules(facts.build(), new GateInputs() {
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
    void exactCommonHooksRequireKnownSupportedDiscoveryAndServerOptimizationSettings() {
        for (boolean client : new boolean[] {false, true}) {
            for (boolean debug : new boolean[] {false, true}) {
                for (boolean commonMaster : new boolean[] {false, true}) {
                    for (boolean toggle : new boolean[] {false, true}) {
                        GateFactsBuilder facts = new GateFactsBuilder();
                        facts.physicalClient = client;
                        facts.modListKnown = true;
                        facts.craftTweakerRemovalsCompatible = true;
                        facts.commonMasterEnabled = commonMaster;
                        facts.indexCraftTweakerRecipeRemovals = toggle;
                        for (String hook : HOOKS) {
                            assertEquals(client || (commonMaster && toggle),
                                    rules(facts, debug).shouldApply(BASE + "removals." + hook));
                            facts.craftTweakerRemovalsCompatible = false; // absent or unsupported version
                            assertFalse(rules(facts, debug).shouldApply(BASE + "removals." + hook));
                            facts.craftTweakerRemovalsCompatible = true;
                            facts.modListKnown = false;
                            assertFalse(rules(facts, debug).shouldApply(BASE + "removals." + hook));
                            facts.modListKnown = true;
                            facts.modDiscoveryFailed = true;
                            assertFalse(rules(facts, debug).shouldApply(BASE + "removals." + hook));
                            facts.modDiscoveryFailed = false;
                        }
                        assertFalse(rules(facts, debug).shouldApply(BASE + "removals.FutureMixin"));
                    }
                }
            }
        }
    }

    @Test
    void compareModeRetainsOnlyClientContextAndNeverPortsGeneralParallelRemoval() {
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.modListKnown = true;
        facts.craftTweakerRemovalsCompatible = true;
        facts.indexCraftTweakerRecipeRemovals = true;
        facts.commonMasterEnabled = false; // bootstrap common master is false in Compare Mode
        facts.craftTweakerLoaded = true;
        for (String hook : HOOKS) assertFalse(rules(facts, true).shouldApply(BASE + "removals." + hook));
        assertFalse(rules(facts, true).shouldApply(BASE + "RecipeListParallelRemovalMixin"));
        facts.physicalClient = true;
        for (String hook : HOOKS) assertTrue(rules(facts, false).shouldApply(BASE + "removals." + hook));
        assertTrue(rules(facts, false).shouldApply(BASE + "RecipeListParallelRemovalMixin"));
    }

    @Test
    void newSettingDefaultsOnAndPublishesIndependentBootstrapEvidence() {
        var spec = VHAcceleratorConfig.COMMON_SPEC.getSpec().get(
                List.of("optimizations", "indexCraftTweakerRecipeRemovals"));
        assertEquals(Boolean.TRUE, ((net.minecraftforge.common.ForgeConfigSpec.ValueSpec) spec).getDefault());
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.craftTweakerRemovalsCompatible = true;
        facts.indexCraftTweakerRecipeRemovals = false;
        assertEquals(true, facts.build().diagnosticFacts().get("craftTweakerRemovalsCompatible"));
        assertEquals(false, facts.build().diagnosticFacts().get("indexCraftTweakerRecipeRemovals"));
    }
}
