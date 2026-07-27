package dev.hoyin1600p.vhaccelerator.client.compat.jer;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import jeresources.proxy.CommonProxy;
import jeresources.registry.DungeonRegistry;
import jeresources.registry.MobRegistry;
import jeresources.registry.PlantRegistry;
import jeresources.registry.VillagerRegistry;
import jeresources.registry.WorldGenRegistry;
import net.minecraft.client.Minecraft;

public final class JerCompatibilityCache {
    private static boolean initialized;

    private JerCompatibilityCache() {
    }

    public static void ensureInitialized(CommonProxy proxy) {
        if (!VHAcceleratorClientConfig.VALUES.enableClientOptimizations.get()
                || !VHAcceleratorClientConfig.VALUES.cacheJerCompatibility.get()) {
            proxy.initCompatibility();
            return;
        }
        if (initialized) {
            logReuse();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            throw new IllegalStateException("JER compatibility must be initialized on the client thread");
        }

        long started = System.nanoTime();
        proxy.initCompatibility();
        initialized = true;
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        VHAccelerator.LOGGER.info(
                "Cached JER compatibility in {} ms ({} mobs, {} dungeons, {} plants, "
                        + "{} villagers, {} world-gen entries)",
                elapsedMillis,
                MobRegistry.getInstance().getMobs().size(),
                DungeonRegistry.getInstance().getDungeons().size(),
                PlantRegistry.getInstance().getAllPlants().size(),
                VillagerRegistry.getInstance().getVillagers().size(),
                WorldGenRegistry.getInstance().getWorldGen().size()
        );
    }

    private static void logReuse() {
        VHAccelerator.LOGGER.debug("Reusing cached JER compatibility registries");
    }
}
