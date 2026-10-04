package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.model.deferred.DeferredItemModelOwner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Debug view of what a model bakery keeps alive. Forge stores the latest
 * bakery in {@code ForgeModelBakery.instance} until the next reload, so its
 * maps stay reachable for the whole session.
 */
public interface BakeryRetention {
    Map<?, ?> vhaccelerator$unbakedCache();

    Map<?, ?> vhaccelerator$bakedCache();

    Map<?, ?> vhaccelerator$topLevelModels();

    Map<?, ?> vhaccelerator$bakedTopLevelModels();

    /**
     * Replaces the load-time maps nothing reads after the model manager has
     * applied (dynamic model stage S2). {@code topLevelModels} is always
     * released; the intermediate {@code bakedCache} only when no deferred
     * bake will need it to share sub-models. The unbaked cache stays, since
     * runtime {@code getModel} callers rely on it. Returns the entries freed.
     */
    int vhaccelerator$releaseLoadMaps(boolean includeBakedCache);

    /** Applies S2 to the bakery the model manager just applied. */
    static void releaseAfterApply(Object bakery) {
        if (bakery instanceof BakeryRetention audited
                && dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            try {
                DeferralScopeAudit.report(
                        audited.vhaccelerator$topLevelModels(),
                        audited.vhaccelerator$unbakedCache()
                );
            } catch (RuntimeException | LinkageError failure) {
                VHAccelerator.LOGGER.debug("Could not audit the block-state deferral scope", failure);
            }
        }
        if (!(bakery instanceof BakeryRetention retention)
                || !VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .releaseBakeryLoadMaps)) {
            return;
        }
        boolean deferred = bakery instanceof DeferredItemModelOwner owner
                && !owner.vhaccelerator$deferredItemModels().isEmpty();
        if (bakery instanceof DeferredItemModelOwner owner) {
            owner.vhaccelerator$releaseSelections();
        }
        try {
            int released = retention.vhaccelerator$releaseLoadMaps(!deferred);
            VHAccelerator.LOGGER.info(
                    "Released {} entries of load-time model bakery maps after model loading ({}); "
                            + "the unbaked cache and baked model registry are kept",
                    released,
                    deferred ? "top-level map only; deferred bakes still share the baked cache"
                            : "top-level map and intermediate baked cache");
        } catch (RuntimeException failure) {
            VHAccelerator.LOGGER.warn("Could not release load-time model bakery maps", failure);
        }
    }

    /** Logs map sizes and the most common distinct value classes. */
    static void report(Object bakery) {
        if (!(bakery instanceof BakeryRetention retention)) {
            VHAccelerator.LOGGER.info("[debug] Retained model bakery: unavailable ({})",
                    bakery == null ? "none" : bakery.getClass().getName());
            return;
        }
        VHAccelerator.LOGGER.info(
                "[debug] Retained model bakery ({}): unbakedCache={} entries [{}], bakedCache={} entries [{}], "
                        + "topLevelModels={} entries, bakedTopLevelModels={} entries ({})",
                bakery.getClass().getSimpleName(),
                size(retention.vhaccelerator$unbakedCache()),
                distinctClasses(retention.vhaccelerator$unbakedCache()),
                size(retention.vhaccelerator$bakedCache()),
                distinctClasses(retention.vhaccelerator$bakedCache()),
                size(retention.vhaccelerator$topLevelModels()),
                size(retention.vhaccelerator$bakedTopLevelModels()),
                retention.vhaccelerator$bakedTopLevelModels() == null ? "none"
                        : retention.vhaccelerator$bakedTopLevelModels().getClass().getSimpleName());
    }

    private static int size(Map<?, ?> map) {
        return map == null ? -1 : map.size();
    }

    private static String distinctClasses(Map<?, ?> map) {
        if (map == null) {
            return "none";
        }
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, Integer> counts = new HashMap<>();
        for (Object value : List.copyOf(map.values())) {
            if (value != null && seen.add(value)) {
                counts.merge(value.getClass().getSimpleName(), 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        StringBuilder text = new StringBuilder().append(seen.size()).append(" distinct");
        for (int index = 0; index < Math.min(6, sorted.size()); index++) {
            text.append(index == 0 ? ": " : ", ").append(sorted.get(index).getKey())
                    .append('=').append(sorted.get(index).getValue());
        }
        return text.toString();
    }
}
