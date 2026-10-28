package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class PresizeStateIdMapGateTest {
    private static final String BASE = "dev.hoyin1600p.vhaccelerator.mixin.statemap.";

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

    @Test void bothMixinsFollowTheSettingOnEitherSide() {
        for (boolean client : new boolean[] {false, true}) {
            for (boolean setting : new boolean[] {false, true}) {
                GateFactsBuilder facts = new GateFactsBuilder();
                facts.physicalClient = client;
                facts.presizeBlockStateIdMap = setting;
                for (String mixin : List.of("IdMapperSlotsMixin", "ForgeRegistryStateIdMapMixin", "FutureMixin")) {
                    assertEquals(setting, rules(facts).shouldApply(BASE + mixin), "client=" + client + " " + mixin);
                }
            }
        }
    }

    @Test void settingIsADocumentedCommonDefaultOnKey() {
        var spec = (net.minecraftforge.common.ForgeConfigSpec.ValueSpec)
                VHAcceleratorConfig.COMMON_SPEC.getSpec().get(List.of("optimizations", "presizeBlockStateIdMap"));
        assertEquals(Boolean.TRUE, spec.getDefault());
        assertTrue(spec.getComment().contains("Restart required"));
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.presizeBlockStateIdMap = true;
        assertEquals(true, facts.build().diagnosticFacts().get("presizeBlockStateIdMap"));
    }
}
