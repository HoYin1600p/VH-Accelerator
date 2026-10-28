package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

class CrystalBuddingGateTest {
    private static final String MIXIN = "dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters."
            + "CrystalBuddingChunkLoadMixin";

    @Test
    void buddingGateNeedsOnlyVaultPresenceOnEitherPhysicalSide() {
        // Any config/ownership query at gate time is a bug: the setting must be read live.
        GateInputs inputs = (GateInputs) Proxy.newProxyInstance(GateInputs.class.getClassLoader(),
                new Class<?>[] {GateInputs.class}, (proxy, method, args) -> {
                    throw new AssertionError("Unexpected gate input: " + method.getName());
                });
        for (boolean client : new boolean[] {false, true}) {
            for (boolean vault : new boolean[] {false, true}) {
                GateFactsBuilder facts = new GateFactsBuilder();
                facts.physicalClient = client;
                facts.vaultHuntersLoaded = vault;
                assertEquals(vault, new MixinGateRules(facts.build(), inputs).shouldApply(MIXIN));
            }
        }
    }

    @Test
    void existingVaultClientHooksRemainDisabledOnDedicatedServer() {
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.vaultHuntersLoaded = true;
        GateInputs inputs = (GateInputs) Proxy.newProxyInstance(GateInputs.class.getClassLoader(),
                new Class<?>[] {GateInputs.class}, (proxy, method, args) -> false);
        assertFalse(new MixinGateRules(facts.build(), inputs).shouldApply(
                "dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters.CascadeFilterFirstMixin"));
    }

    @Test
    void commonCorrectnessSettingDefaultsOnWithoutRestart() {
        ForgeConfigSpec.ValueSpec spec = VHAcceleratorConfig.COMMON_SPEC.getSpec().get(
                List.of("compatibility", "fixBuddingCrystalChunkLoad"));
        assertEquals(Boolean.TRUE, spec.getDefault());
        assertFalse(spec.needsWorldRestart());
        assertTrue(spec.getComment().contains("Read live"));
    }
}
