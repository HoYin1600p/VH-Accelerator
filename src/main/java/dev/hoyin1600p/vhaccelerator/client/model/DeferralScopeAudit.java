package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

/**
 * Debug-only: sizes how much of the block-state model set deferred baking
 * could cover, and what keeps the rest eager. Reads the bakery's maps; never
 * loads or bakes anything.
 */
final class DeferralScopeAudit {
    private DeferralScopeAudit() {
    }

    @SuppressWarnings("unchecked")
    static void report(Map<?, ?> topLevel, Map<?, ?> unbaked) {
        long started = System.nanoTime();
        Map<ResourceLocation, UnbakedModel> topLevelModels = (Map<ResourceLocation, UnbakedModel>) topLevel;
        Map<ResourceLocation, UnbakedModel> unbakedCache = (Map<ResourceLocation, UnbakedModel>) unbaked;
        UnbakedModel missing = unbakedCache.get(ModelBakery.MISSING_MODEL_LOCATION);
        DeferredItemModelSelector.Graph<ResourceLocation, UnbakedModel> graph =
                new DeferredItemModelSelector.Graph<>() {
                    @Override
                    public UnbakedModel cached(ResourceLocation location) {
                        return location == null || location.getPath().startsWith("builtin/")
                                ? null
                                : unbakedCache.get(location);
                    }

                    @Override
                    public Collection<ResourceLocation> dependencies(UnbakedModel node) {
                        return node.getDependencies();
                    }

                    @Override
                    public boolean isPlain(UnbakedModel node) {
                        return DeferredBlockStateBaking.plainNode(node, missing, true);
                    }
                };
        // Every non-inventory block-state key whose graph is plain, ignoring
        // the namespace exclusions.
        Set<ResourceLocation> plain = DeferredItemModelSelector.select(
                topLevelModels,
                location -> location instanceof ModelResourceLocation model
                        && !"inventory".equals(model.getVariant()),
                graph
        );
        Map<String, int[]> byReason = new HashMap<>();
        int blockStates = 0;
        for (Map.Entry<ResourceLocation, UnbakedModel> entry : topLevelModels.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!(location instanceof ModelResourceLocation model)
                    || "inventory".equals(model.getVariant())) {
                continue;
            }
            blockStates++;
            String namespace = location.getNamespace();
            String reason;
            if (plain.contains(location)) {
                reason = DeferredBlockStateBaking.eligibleKey(location)
                        ? "deferrable"
                        : "plain, excluded namespace " + namespace;
            } else if (entry.getValue() == null || graph.cached(location) != entry.getValue()) {
                reason = "not in unbaked cache";
            } else {
                reason = "non-plain graph " + namespace;
            }
            byReason.computeIfAbsent(reason, key -> new int[1])[0]++;
        }
        List<Map.Entry<String, int[]>> rows = byReason.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .limit(16)
                .toList();
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, int[]> row : rows) {
            text.append("\n    ").append(row.getValue()[0]).append("  ").append(row.getKey());
        }
        VHAccelerator.LOGGER.info(
                "[debug] Block-state deferral scope over {} block-state keys ({} ms):{}",
                blockStates,
                (System.nanoTime() - started) / 1_000_000L,
                text
        );
    }
}
