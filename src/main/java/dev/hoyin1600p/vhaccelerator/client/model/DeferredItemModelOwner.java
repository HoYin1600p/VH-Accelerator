package dev.hoyin1600p.vhaccelerator.client.model;

import java.util.Collections;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Implemented on ModelBakery by the deferred item-model mixin. */
public interface DeferredItemModelOwner {
    /** Selects once per bakery; empty whenever deferral is inactive. */
    Set<ResourceLocation> vhaccelerator$deferredItemModels();

    /** Drops the load-time selection sets once the model manager has applied. */
    void vhaccelerator$releaseSelections();

    static Set<ResourceLocation> deferredFor(Object bakery) {
        return bakery instanceof DeferredItemModelOwner owner
                ? owner.vhaccelerator$deferredItemModels()
                : Collections.emptySet();
    }
}
