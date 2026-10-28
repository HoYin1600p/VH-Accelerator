package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.compat.buildinggadgets.BuildingGadgetsTemplateCompaction;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildingGadgetsTemplateGateTest {
    private static MixinGateRules rules(GateFactsBuilder facts) {
        return new MixinGateRules(facts.build(), new GateInputs() {
            public boolean debugEnabled() { return false; }
            public boolean vhaOwns(BackportFeature feature) { return false; }
            public boolean modernFixOptionEnabled(String option) { return false; }
            public boolean modernFixDynamicResourcesEnabled() { return false; }
            public void buildScapeInteropApplied() { }
            public void ctmMixinApplied() { }
            public void modernFixBakeDecision(boolean enabled) { }
        });
    }

    @Test void onlyDedicatedKnownReviewedExplicitlyEnabledGroupCanApply() {
        for (boolean client : new boolean[] {false, true}) {
            for (boolean known : new boolean[] {false, true}) {
                for (boolean compatible : new boolean[] {false, true}) {
                    for (boolean master : new boolean[] {false, true}) {
                        for (boolean setting : new boolean[] {false, true}) {
                            GateFactsBuilder facts = new GateFactsBuilder();
                            facts.physicalClient = client;
                            facts.modListKnown = known;
                            facts.buildingGadgetsTemplatesCompatible = compatible;
                            facts.commonMasterEnabled = master;
                            facts.compactBuildingGadgetsTemplates = setting;
                            assertEquals(!client && known && compatible && master && setting,
                                    rules(facts).shouldApply(BuildingGadgetsTemplateCompaction.MIXIN));
                            facts.modDiscoveryFailed = true;
                            assertFalse(rules(facts).shouldApply(BuildingGadgetsTemplateCompaction.MIXIN));
                            assertFalse(rules(facts).shouldApply(
                                    "dev.hoyin1600p.vhaccelerator.mixin.compat.buildinggadgets.FutureMixin"));
                        }
                    }
                }
            }
        }
    }

    @Test void compareModeCommonMasterRefusalAndDefaultOffAreIndependentEvidence() {
        var spec = (net.minecraftforge.common.ForgeConfigSpec.ValueSpec)
                VHAcceleratorConfig.COMMON_SPEC.getSpec().get(List.of("compatibility", "compactBuildingGadgetsTemplates"));
        assertEquals(Boolean.TRUE, spec.getDefault());
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.modListKnown = true;
        facts.buildingGadgetsTemplatesCompatible = true;
        facts.compactBuildingGadgetsTemplates = true;
        facts.commonMasterEnabled = false;
        assertFalse(rules(facts).shouldApply(BuildingGadgetsTemplateCompaction.MIXIN));
        assertEquals(true, facts.build().diagnosticFacts().get("compactBuildingGadgetsTemplates"));
        assertEquals(false, facts.build().diagnosticFacts().get("commonMasterEnabled"));
    }
}
