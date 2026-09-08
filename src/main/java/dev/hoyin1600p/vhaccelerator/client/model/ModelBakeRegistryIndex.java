package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

/** Launch-scoped classification shared by ordered model-bake callbacks. */
public final class ModelBakeRegistryIndex {
    private static Map<ResourceLocation, BakedModel> activeRegistry;
    private static NamespaceIndex<ResourceLocation, BakedModel> index;
    private static int filteredViews;
    private static long avoidedVisits;

    private ModelBakeRegistryIndex() { }

    public static synchronized void begin(Map<ResourceLocation, BakedModel> registry) {
        activeRegistry = registry;
        index = new NamespaceIndex<>(registry, ResourceLocation::getNamespace);
        filteredViews = 0;
        avoidedVisits = 0;
    }

    private static NamespaceIndex<ResourceLocation, BakedModel> index(Map<ResourceLocation, BakedModel> registry) {
        if (registry != activeRegistry || index == null) { begin(registry); }
        return index;
    }

    public static synchronized Set<ResourceLocation> keys(Map<ResourceLocation, BakedModel> registry, String namespace) {
        if (!enabled()) { return registry.keySet(); }
        Set<ResourceLocation> view = index(registry).keys(namespace);
        count(registry.size(), view.size());
        return view;
    }

    public static synchronized Set<Map.Entry<ResourceLocation, BakedModel>> entries(
            Map<ResourceLocation, BakedModel> registry, String namespace) {
        if (!enabled()) { return registry.entrySet(); }
        Set<Map.Entry<ResourceLocation, BakedModel>> view = index(registry).entries(namespace);
        count(registry.size(), view.size());
        return view;
    }

    public static synchronized void replaceAll(Map<ResourceLocation, BakedModel> registry,
            Collection<String> namespaces,
            BiFunction<? super ResourceLocation, ? super BakedModel, ? extends BakedModel> replacement) {
        if (!enabled()) { registry.replaceAll(replacement); return; }
        int[] visited = {0};
        index(registry).replaceAll(namespaces, (key, model) -> {
            visited[0]++;
            return replacement.apply(key, model);
        });
        count(registry.size(), visited[0]);
    }

    private static void count(int total, int visited) {
        filteredViews++;
        avoidedVisits += Math.max(0, total - visited);
    }

    public static synchronized void finish() {
        if (index != null && index.builds() > 0) {
            VHAccelerator.LOGGER.info(
                    "Indexed {} baked-model namespaces in {} ms ({} rebuilds); served {} backed callback views and avoided {} unrelated visits",
                    index.namespaces(), index.buildNanos() / 1_000_000L, index.builds(), filteredViews, avoidedVisits);
        }
        activeRegistry = null;
        index = null;
    }

    private static boolean enabled() {
        return VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(VHAcceleratorClientConfig.VALUES.indexModelBakeRegistries);
    }
}
