package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

/**
 * Debug-only: what the retained unbaked cache holds at launch completion.
 * Block-state keys are split by whether their block was skipped (a skipped
 * block's key here means its graph was loaded after all).
 */
public final class UnbakedCacheCensus {
    private UnbakedCacheCensus() {
    }

    public static void report(Map<?, ?> unbakedCache) {
        if (unbakedCache == null) {
            return;
        }
        long started = System.nanoTime();
        Map<String, int[]> groups = new HashMap<>();
        int skippedBlockKeys = 0;
        for (Object key : unbakedCache.keySet()) {
            if (!(key instanceof ResourceLocation location)) {
                continue;
            }
            String kind;
            if (location instanceof ModelResourceLocation model) {
                if ("inventory".equals(model.getVariant())) {
                    kind = "item-key";
                } else if (BlockGraphSkipSession.wasSkippedBlock(location)) {
                    kind = "state-key(skipped block)";
                    skippedBlockKeys++;
                } else {
                    kind = "state-key";
                }
            } else {
                String path = location.getPath();
                int slash = path.indexOf('/');
                kind = "file " + (slash > 0 ? path.substring(0, slash) : path);
            }
            groups.computeIfAbsent(location.getNamespace() + " " + kind, k -> new int[1])[0]++;
        }
        StringBuilder text = new StringBuilder();
        groups.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .limit(22)
                .forEach(row -> text.append("\n    ").append(row.getValue()[0]).append("  ").append(row.getKey()));
        VHAccelerator.LOGGER.info(
                "[debug] Unbaked cache census: {} entries, {} keys of skipped blocks; {} ({} ms):{}",
                unbakedCache.size(), skippedBlockKeys, BlockGraphSkipSession.describeCurrent(),
                (System.nanoTime() - started) / 1_000_000L, text);
    }
}
