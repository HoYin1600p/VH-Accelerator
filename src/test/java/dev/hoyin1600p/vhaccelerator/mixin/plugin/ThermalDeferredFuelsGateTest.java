package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.compat.thermalfuels.ThermalConvertedFuels;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThermalDeferredFuelsGateTest {
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

    @Test void appliesOnDedicatedServersOnlyWithKnownDiscoveryMasterAndFact() {
        for (String mixin : List.of(ThermalConvertedFuels.STIRLING_MIXIN, ThermalConvertedFuels.GOURMAND_MIXIN)) {
            for (boolean client : new boolean[] {false, true}) {
                for (boolean known : new boolean[] {false, true}) {
                    for (boolean master : new boolean[] {false, true}) {
                        for (boolean fact : new boolean[] {false, true}) {
                            GateFactsBuilder facts = new GateFactsBuilder();
                            facts.physicalClient = client;
                            facts.modListKnown = known;
                            facts.commonMasterEnabled = master;
                            facts.thermalDeferredFuelLists = fact;
                            assertEquals(!client && known && master && fact, rules(facts).shouldApply(mixin));
                            facts.modDiscoveryFailed = true;
                            assertFalse(rules(facts).shouldApply(mixin));
                        }
                    }
                }
            }
        }
    }

    @Test void futureMembersOfTheGroupAreRejected() {
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.modListKnown = true;
        facts.commonMasterEnabled = true;
        facts.thermalDeferredFuelLists = true;
        assertFalse(rules(facts).shouldApply(ThermalConvertedFuels.GROUP + ".FutureMixin"));
    }

    @Test void theClientThermalGroupIsUnaffected() {
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.physicalClient = false;
        facts.thermalLoaded = true;
        facts.thermalDeferredFuelLists = true;
        facts.modListKnown = true;
        facts.commonMasterEnabled = true;
        assertFalse(rules(facts).shouldApply("dev.hoyin1600p.vhaccelerator.mixin.compat.thermal.StirlingFuelManagerMixin"));
    }

    @Test void settingIsADocumentedCommonDefaultOnKey() {
        var spec = (net.minecraftforge.common.ForgeConfigSpec.ValueSpec)
                VHAcceleratorConfig.COMMON_SPEC.getSpec().get(List.of("compatibility", "deferThermalConvertedFuels"));
        assertEquals(Boolean.TRUE, spec.getDefault());
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.thermalDeferredFuelLists = true;
        assertEquals(true, facts.build().diagnosticFacts().get("thermalDeferredFuelLists"));
    }
}
