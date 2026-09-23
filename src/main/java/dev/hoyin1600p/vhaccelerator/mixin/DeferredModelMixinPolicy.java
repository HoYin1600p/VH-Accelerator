package dev.hoyin1600p.vhaccelerator.mixin;

/** Startup selection for the existing deferred-item model mixins. */
public final class DeferredModelMixinPolicy {
    private DeferredModelMixinPolicy() {
    }

    /**
     * @param modernFixDynamicResourcesEnabled {@code false} only when ModernFix
     *     dynamic resources are verified disabled; {@code null} is unverified
     */
    public static boolean allowDeferredItemMixins(
            boolean physicalClient,
            boolean modDiscoverySucceeded,
            boolean ctmInstalled,
            boolean modernFixLoaded,
            Boolean modernFixDynamicResourcesEnabled
    ) {
        if (!physicalClient || !modDiscoverySucceeded || ctmInstalled) {
            return false;
        }
        if (!modernFixLoaded) {
            return true;
        }
        return Boolean.FALSE.equals(modernFixDynamicResourcesEnabled);
    }
}
