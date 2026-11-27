package dev.hoyin1600p.vhaccelerator.client.compat.xaero;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;

/**
 * Decides whether Xaero's title-screen update notice stays closed. The setting
 * is read on every call, so changing it applies without a restart. This is a
 * compatibility fix, so it does not depend on VHA's optimizations switch or
 * Compare Mode, and it suppresses by default while the config is not loaded.
 */
public final class XaeroUpdateScreenSuppressor {
    private XaeroUpdateScreenSuppressor() {
    }

    public static boolean enabled() {
        try {
            return VHAcceleratorClientConfig.VALUES.suppressXaeroUpdateScreen.get();
        } catch (RuntimeException configNotLoaded) {
            return true;
        }
    }
}
