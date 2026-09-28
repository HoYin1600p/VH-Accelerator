package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.Map;

/**
 * A block model that memoizes its material list during the material pass.
 * The memo only speeds up that pass; baking never reads it, so it is dropped
 * once loading completes instead of living on every retained unbaked model.
 */
public interface MaterialMemoHolder {
    void vhaccelerator$clearMaterialMemo();

    /** Clears every memo in a bakery's unbaked cache after the material pass. */
    static void releaseAll(Map<?, ?> unbakedCache) {
        if (unbakedCache == null
                || !VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.releaseCacheMemoryAfterUse
                )) {
            return;
        }
        for (Object model : unbakedCache.values()) {
            if (model instanceof MaterialMemoHolder holder) {
                holder.vhaccelerator$clearMaterialMemo();
            }
        }
    }
}
