package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecLayouts;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class LazyStatePropertyCodecGateTest {
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

    @Test void appliesOnlyOnDedicatedServersWithDiscoveryMasterAndSetting() {
        for (boolean client : new boolean[] {false, true}) {
            for (boolean known : new boolean[] {false, true}) {
                for (boolean master : new boolean[] {false, true}) {
                    for (boolean setting : new boolean[] {false, true}) {
                        GateFactsBuilder facts = new GateFactsBuilder();
                        facts.physicalClient = client;
                        facts.modListKnown = known;
                        facts.commonMasterEnabled = master;
                        facts.lazyStatePropertyCodecs = setting;
                        assertEquals(!client && known && master && setting,
                                rules(facts).shouldApply(StateCodecLayouts.MIXIN),
                                "client=" + client + " known=" + known + " master=" + master + " setting=" + setting);
                        facts.modDiscoveryFailed = true;
                        assertFalse(rules(facts).shouldApply(StateCodecLayouts.MIXIN));
                        assertFalse(rules(facts).shouldApply(StateCodecLayouts.GROUP + ".FutureMixin"));
                    }
                }
            }
        }
    }

    @Test void settingIsADocumentedCommonDefaultOnKey() {
        var spec = (net.minecraftforge.common.ForgeConfigSpec.ValueSpec)
                VHAcceleratorConfig.COMMON_SPEC.getSpec().get(List.of("optimizations", "lazyBlockStatePropertyCodecs"));
        assertEquals(Boolean.TRUE, spec.getDefault());
        assertTrue(spec.getComment().contains("Restart required"));
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.lazyStatePropertyCodecs = true;
        assertEquals(true, facts.build().diagnosticFacts().get("lazyStatePropertyCodecs"));
    }
}
