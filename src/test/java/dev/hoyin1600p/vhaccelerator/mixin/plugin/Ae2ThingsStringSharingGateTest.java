package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.compat.ae2things.Ae2ThingsDiskStrings;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ae2ThingsStringSharingGateTest {
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

    @Test void appliesOnBothSidesOnlyWithKnownDiscoveryMasterAndFact() {
        for (boolean client : new boolean[] {false, true}) {
            for (boolean known : new boolean[] {false, true}) {
                for (boolean master : new boolean[] {false, true}) {
                    for (boolean sharing : new boolean[] {false, true}) {
                        GateFactsBuilder facts = new GateFactsBuilder();
                        facts.physicalClient = client;
                        facts.modListKnown = known;
                        facts.commonMasterEnabled = master;
                        facts.ae2ThingsStringSharing = sharing;
                        assertEquals(known && master && sharing, rules(facts).shouldApply(Ae2ThingsDiskStrings.MIXIN));
                        facts.modDiscoveryFailed = true;
                        assertFalse(rules(facts).shouldApply(Ae2ThingsDiskStrings.MIXIN));
                        assertFalse(rules(facts).shouldApply(
                                "dev.hoyin1600p.vhaccelerator.mixin.compat.ae2things.FutureMixin"));
                    }
                }
            }
        }
    }

    @Test void absentModLeavesTheMixinOutEvenWithTheSettingOn() {
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.modListKnown = true;
        facts.commonMasterEnabled = true;
        // The discovery fact folds in mod presence; without the mod it stays false.
        facts.ae2ThingsStringSharing = false;
        assertFalse(rules(facts).shouldApply(Ae2ThingsDiskStrings.MIXIN));
    }

    @Test void settingIsADocumentedCommonDefaultOnKey() {
        var spec = (net.minecraftforge.common.ForgeConfigSpec.ValueSpec)
                VHAcceleratorConfig.COMMON_SPEC.getSpec().get(List.of("compatibility", "shareAe2ThingsDiskStrings"));
        assertEquals(Boolean.TRUE, spec.getDefault());
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.ae2ThingsStringSharing = true;
        assertEquals(true, facts.build().diagnosticFacts().get("ae2ThingsStringSharing"));
    }
}
