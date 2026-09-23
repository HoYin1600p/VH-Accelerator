package dev.hoyin1600p.vhaccelerator.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeferredModelMixinPolicyTest {
    @Test
    void ctmPresentOmitsDeferredItemMixins() {
        assertFalse(allow(true, true, true, false, null));
        assertFalse(allow(true, true, true, true, Boolean.FALSE));
    }

    @Test
    void ctmAbsentPreservesKnownClientSelection() {
        assertTrue(allow(true, true, false, false, null));
        assertTrue(allow(true, true, false, true, Boolean.FALSE));
    }

    @Test
    void unknownModListOrFailedDiscoveryFailsClosed() {
        assertFalse(allow(true, false, false, false, Boolean.FALSE));
        assertFalse(allow(true, false, true, true, null));
    }

    @Test
    void dedicatedServerOmitsDeferredItemMixins() {
        assertFalse(allow(false, true, false, false, Boolean.FALSE));
        assertFalse(allow(false, true, false, true, Boolean.FALSE));
    }

    @Test
    void modernFixDynamicResourcesOnOffAndUnknown() {
        assertFalse(allow(true, true, false, true, Boolean.TRUE));
        assertTrue(allow(true, true, false, true, Boolean.FALSE));
        assertFalse(allow(true, true, false, true, null));
    }

    private static boolean allow(
            boolean physicalClient,
            boolean modDiscoverySucceeded,
            boolean ctmInstalled,
            boolean modernFixLoaded,
            Boolean modernFixDynamicResourcesEnabled
    ) {
        return DeferredModelMixinPolicy.allowDeferredItemMixins(
                physicalClient,
                modDiscoverySucceeded,
                ctmInstalled,
                modernFixLoaded,
                modernFixDynamicResourcesEnabled
        );
    }
}
