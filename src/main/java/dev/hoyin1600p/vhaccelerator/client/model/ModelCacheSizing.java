package dev.hoyin1600p.vhaccelerator.client.model;

import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import java.util.HashMap;
import java.util.Map;

public final class ModelCacheSizing {
    private ModelCacheSizing() {
    }

    /** Capacity is a hint only: every map remains free to grow. */
    public static int hashMapCapacity(int expectedEntries) {
        if (expectedEntries < 0) throw new IllegalArgumentException("Negative entry count");
        if (expectedEntries < 3) return expectedEntries + 1;
        if (expectedEntries >= 1 << 29) return 1 << 30;
        return (int) (expectedEntries / 0.75F + 1.0F);
    }

    public static <K, V> Map<K, V> reservePlainEmptyMap(
            Map<K, V> existing, int expectedEntries) {
        if (!existing.isEmpty() || existing.getClass() != HashMap.class) return existing;
        return new HashMap<>(hashMapCapacity(expectedEntries));
    }

    public static int topLevelEstimate() {
        long estimate = Registry.ITEM.size() + 64L;
        for (Block block : Registry.BLOCK) {
            estimate += block.getStateDefinition()
                    .getPossibleStates()
                    .size();
            if (estimate >= Integer.MAX_VALUE - 8L) {
                return Integer.MAX_VALUE - 8;
            }
        }
        return (int) estimate;
    }
}
