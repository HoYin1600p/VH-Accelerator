package dev.hoyin1600p.vhaccelerator.client.model;

import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;

public final class ModelCacheSizing {
    private ModelCacheSizing() {
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
