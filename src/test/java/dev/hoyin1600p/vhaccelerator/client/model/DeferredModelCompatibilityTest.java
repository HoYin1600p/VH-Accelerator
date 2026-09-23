package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeferredModelCompatibilityTest {
    @Test
    void ctmDisablesEveryDeferredModelPath() {
        assertFalse(DeferredModelCompatibility.allowsDeferral(true, true));
    }

    @Test
    void absentCtmAllowsConfiguredDeferral() {
        assertTrue(DeferredModelCompatibility.allowsDeferral(true, false));
    }

    @Test
    void unknownModDiscoveryFailsClosed() {
        assertFalse(DeferredModelCompatibility.allowsDeferral(false, false));
        assertFalse(DeferredModelCompatibility.allowsDeferral(false, true));
    }
}
