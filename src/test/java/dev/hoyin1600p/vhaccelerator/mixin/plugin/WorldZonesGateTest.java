package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;
import dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldZonesGateTest {
    @Test void onlyKnownDedicatedSelectionAndCommonMasterAdmitTheMixin() {
        GateInputs unused = (GateInputs) Proxy.newProxyInstance(GateInputs.class.getClassLoader(), new Class<?>[]{GateInputs.class},
                (proxy, method, args) -> { throw new AssertionError("Unexpected read " + method.getName()); });
        for (int flags = 0; flags < 64; flags++) {
            GateFactsBuilder f = new GateFactsBuilder();
            f.physicalClient = (flags & 1) != 0; f.modListKnown = (flags & 2) != 0;
            f.modDiscoveryFailed = (flags & 4) != 0; f.vaultHuntersLoaded = (flags & 8) != 0;
            f.commonMasterEnabled = (flags & 16) != 0; f.worldZonesCompaction = (flags & 32) != 0;
            boolean expected = !f.physicalClient && f.modListKnown && !f.modDiscoveryFailed
                    && f.vaultHuntersLoaded && f.commonMasterEnabled && f.worldZonesCompaction;
            assertEquals(expected, new MixinGateRules(f.build(), unused).shouldApply(WorldZonesLayouts.MIXIN));
        }
        var spec = (net.minecraftforge.common.ForgeConfigSpec.ValueSpec) VHAcceleratorConfig.COMMON_SPEC.getSpec()
                .get(List.of("compatibility", "compactWorldZonesMemberships"));
        assertEquals(Boolean.TRUE, spec.getDefault());
    }
}
