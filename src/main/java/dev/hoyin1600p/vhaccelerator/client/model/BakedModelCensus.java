package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Debug-only: which already-baked models remain in the registry, by
 * namespace and kind, with their distinct quad counts. Deferred entries that
 * are not baked yet are skipped, so this never bakes anything.
 */
public final class BakedModelCensus {
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST};

    private BakedModelCensus() {
    }

    public static void report(Map<?, ?> registry) {
        if (registry == null) {
            return;
        }
        long started = System.nanoTime();
        Map<String, long[]> groups = new HashMap<>();
        Map<BakedModel, Boolean> seenModels = new IdentityHashMap<>();
        Map<Object, Boolean> seenQuads = new IdentityHashMap<>();
        Random random = new Random(42L);
        long keys = 0;
        for (Map.Entry<?, ?> entry : registry.entrySet()) {
            if (!(entry.getKey() instanceof ResourceLocation location)
                    || DeferredBlockStateBaking.isUnresolved(location)
                    || DeferredItemModelBaking.isUnresolved(location)) {
                continue;
            }
            keys++;
            String kind = location instanceof ModelResourceLocation model
                    ? ("inventory".equals(model.getVariant()) ? "item" : "block")
                    : "other";
            long[] group = groups.computeIfAbsent(location.getNamespace() + " " + kind, k -> new long[3]);
            group[0]++;
            if (!(entry.getValue() instanceof BakedModel model) || seenModels.put(model, Boolean.TRUE) != null) {
                continue;
            }
            group[1]++;
            try {
                for (Direction side : SIDES) {
                    // Forge's data-aware overload; the vanilla one asserts
                    // on some mod models.
                    List<BakedQuad> quads = model.getQuads(
                            null, side, random, net.minecraftforge.client.model.data.EmptyModelData.INSTANCE);
                    for (BakedQuad quad : quads) {
                        if (seenQuads.put(quad, Boolean.TRUE) == null) {
                            group[2]++;
                        }
                    }
                }
            } catch (RuntimeException | AssertionError | LinkageError ignored) {
                // Custom models may need a block state or model data.
            }
        }
        StringBuilder text = new StringBuilder();
        groups.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[2], a.getValue()[2]))
                .limit(18)
                .forEach(row -> text.append("\n    ").append(row.getValue()[2]).append(" quads, ")
                        .append(row.getValue()[1]).append(" models, ").append(row.getValue()[0])
                        .append(" keys  ").append(row.getKey()));
        VHAccelerator.LOGGER.info(
                "[debug] Baked model census: {} baked keys, {} distinct models, {} distinct quads ({} ms):{}",
                keys, seenModels.size(), seenQuads.size(), (System.nanoTime() - started) / 1_000_000L, text);
    }
}
