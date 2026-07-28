package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

/**
 * A launch-scoped namespace index for ordered model-bake listeners.
 *
 * <p>Several mods independently scan the complete baked registry and discard
 * every namespace except their own. This index performs that classification
 * once while preserving the original map and listener ordering.</p>
 */
public final class ModelBakeRegistryIndex {
    private static Map<ResourceLocation, BakedModel> activeRegistry;
    private static Map<String, List<ResourceLocation>> namespaceKeys =
            Map.of();
    private static int indexedSize = -1;
    private static int fullIndexBuilds;
    private static long indexBuildNanos;
    private static int filteredViews;
    private static long avoidedVisits;

    private ModelBakeRegistryIndex() {
    }

    public static synchronized void begin(
            Map<ResourceLocation, BakedModel> registry
    ) {
        activeRegistry = registry;
        namespaceKeys = Map.of();
        indexedSize = -1;
        fullIndexBuilds = 0;
        indexBuildNanos = 0L;
        filteredViews = 0;
        avoidedVisits = 0L;
    }

    public static synchronized Set<ResourceLocation> keys(
            Map<ResourceLocation, BakedModel> registry,
            String namespace
    ) {
        if (!enabled()) {
            return registry.keySet();
        }
        ensureIndex(registry);
        List<ResourceLocation> keys = namespaceKeys.get(namespace);
        if (keys == null) {
            keys = List.of();
        }
        filteredViews++;
        avoidedVisits += Math.max(0, registry.size() - keys.size());
        return new LinkedHashSet<>(keys);
    }

    public static synchronized Set<Map.Entry<
            ResourceLocation,
            BakedModel>> entries(
            Map<ResourceLocation, BakedModel> registry,
            String namespace
    ) {
        if (!enabled()) {
            return registry.entrySet();
        }
        ensureIndex(registry);
        List<ResourceLocation> keys = namespaceKeys.get(namespace);
        if (keys == null) {
            keys = List.of();
        }
        Set<Map.Entry<ResourceLocation, BakedModel>> entries =
                new LinkedHashSet<>(Math.max(16, keys.size() * 2));
        for (ResourceLocation key : keys) {
            entries.add(new AbstractMap.SimpleImmutableEntry<>(
                    key,
                    registry.get(key)
            ));
        }
        filteredViews++;
        avoidedVisits += Math.max(0, registry.size() - keys.size());
        return entries;
    }

    public static synchronized void finish() {
        if (activeRegistry == null) {
            return;
        }
        if (fullIndexBuilds > 0) {
            VHAccelerator.LOGGER.info(
                    "Indexed {} baked-model namespace set(s) in {} ms; "
                            + "served {} filtered Forge callback view(s) "
                            + "and avoided {} unrelated model visit(s)",
                    namespaceKeys.size(),
                    indexBuildNanos / 1_000_000L,
                    filteredViews,
                    avoidedVisits
            );
        }
        activeRegistry = null;
        namespaceKeys = Map.of();
        indexedSize = -1;
    }

    private static void ensureIndex(
            Map<ResourceLocation, BakedModel> registry
    ) {
        if (registry != activeRegistry) {
            begin(registry);
        }
        if (indexedSize == registry.size()) {
            return;
        }

        long started = System.nanoTime();
        Map<String, List<ResourceLocation>> mutable =
                new LinkedHashMap<>();
        for (ResourceLocation key : registry.keySet()) {
            mutable.computeIfAbsent(
                    key.getNamespace(),
                    ignored -> new ArrayList<>()
            ).add(key);
        }
        Map<String, List<ResourceLocation>> stable =
                new LinkedHashMap<>(mutable.size());
        mutable.forEach((namespace, keys) ->
                stable.put(namespace, List.copyOf(keys)));
        namespaceKeys = Map.copyOf(stable);
        indexedSize = registry.size();
        fullIndexBuilds++;
        indexBuildNanos += System.nanoTime() - started;
    }

    private static boolean enabled() {
        return VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .indexModelBakeRegistries
                );
    }
}
