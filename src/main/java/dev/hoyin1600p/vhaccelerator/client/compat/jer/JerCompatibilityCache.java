package dev.hoyin1600p.vhaccelerator.client.compat.jer;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import jeresources.proxy.CommonProxy;
import jeresources.registry.DungeonRegistry;
import jeresources.registry.MobRegistry;
import jeresources.registry.PlantRegistry;
import jeresources.registry.VillagerRegistry;
import jeresources.registry.WorldGenRegistry;
import jeresources.config.ConfigValues;
import jeresources.util.LootTableHelper;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.level.storage.loot.PredicateManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.resource.PathResourcePack;

public final class JerCompatibilityCache {
    private static final Field JER_LOOT_TABLES = findLootTablesField();

    private static boolean initialized;
    private static boolean preloadAttempted;
    private static long preloadStartedNanos;
    private static long preloadElapsedMillis;
    private static PreloadPhase preloadPhase = PreloadPhase.NOT_STARTED;
    private static LootTables pendingLootTables;
    private static ReloadableResourceManager pendingResourceManager;
    private static ReloadInstance pendingReload;

    private JerCompatibilityCache() {
    }

    public static void beginMenuPreload() {
        if (preloadAttempted
                || !VHAcceleratorClientConfig.VALUES.enableClientOptimizations.get()
                || !VHAcceleratorClientConfig.VALUES.cacheJerCompatibility.get()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()
                || minecraft.level != null
                || minecraft.getConnection() != null) {
            return;
        }

        preloadAttempted = true;
        preloadStartedNanos = System.nanoTime();
        try {
            LootTables existing = getPublishedLootTables();
            if (existing != null) {
                preloadPhase = PreloadPhase.COMPLETED;
                preloadElapsedMillis = 0L;
                return;
            }

            pendingLootTables = new LootTables(new PredicateManager());
            if (ConfigValues.disableLootManagerReloading.get()) {
                publishPreloadedLootTables();
                return;
            }

            pendingResourceManager = new ReloadableResourceManager(PackType.SERVER_DATA);
            List<PackResources> packs = new LinkedList<>();
            packs.add(new VanillaPackResources(
                    ServerPacksSource.BUILT_IN_METADATA,
                    "minecraft"
            ));
            for (IModFileInfo mod : ModList.get().getModFiles()) {
                packs.add(new PathResourcePack(
                        mod.getFile().getFileName(),
                        mod.getFile().getFilePath()
                ));
            }

            pendingResourceManager.registerReloadListener(pendingLootTables);
            pendingReload = pendingResourceManager.createReload(
                    Util.backgroundExecutor(),
                    minecraft,
                    CompletableFuture.completedFuture(Unit.INSTANCE),
                    packs
            );
            preloadPhase = PreloadPhase.RUNNING;
            VHAccelerator.LOGGER.info(
                    "Started asynchronous JER loot-table preload from {} data packs",
                    packs.size()
            );
        } catch (Throwable throwable) {
            failPreload(throwable);
        }
    }

    public static void pollMenuPreload() {
        if (preloadPhase == PreloadPhase.RUNNING
                && pendingReload != null
                && pendingReload.isDone()) {
            finishPreload();
        }
    }

    public static PreloadStatus preloadStatus() {
        int percent = 0;
        if (preloadPhase == PreloadPhase.RUNNING && pendingReload != null) {
            percent = Math.max(
                    0,
                    Math.min(
                            99,
                            Math.round(pendingReload.getActualProgress() * 100.0F)
                    )
            );
        } else if (preloadPhase == PreloadPhase.COMPLETED) {
            percent = 100;
        }

        long elapsedMillis = preloadPhase == PreloadPhase.RUNNING
                ? (System.nanoTime() - preloadStartedNanos) / 1_000_000L
                : preloadElapsedMillis;
        return new PreloadStatus(preloadPhase, percent, elapsedMillis);
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

        awaitMenuPreload(minecraft);
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

    private static void awaitMenuPreload(Minecraft minecraft) {
        if (preloadPhase != PreloadPhase.RUNNING || pendingReload == null) {
            return;
        }

        long waitStarted = System.nanoTime();
        minecraft.managedBlock(pendingReload::isDone);
        finishPreload();
        long waitMillis = (System.nanoTime() - waitStarted) / 1_000_000L;
        if (waitMillis > 0L) {
            VHAccelerator.LOGGER.info(
                    "Waited {} ms for the remaining JER menu preload",
                    waitMillis
            );
        }
    }

    private static void finishPreload() {
        try {
            pendingReload.checkExceptions();
            publishPreloadedLootTables();
        } catch (Throwable throwable) {
            failPreload(throwable);
        }
    }

    private static void publishPreloadedLootTables() throws IllegalAccessException {
        JER_LOOT_TABLES.set(null, pendingLootTables);
        preloadPhase = PreloadPhase.COMPLETED;
        preloadElapsedMillis =
                (System.nanoTime() - preloadStartedNanos) / 1_000_000L;
        pendingLootTables = null;
        pendingResourceManager = null;
        pendingReload = null;
        VHAccelerator.LOGGER.info(
                "JER loot-table menu preload completed in {} ms",
                preloadElapsedMillis
        );
    }

    private static LootTables getPublishedLootTables() throws IllegalAccessException {
        return (LootTables) JER_LOOT_TABLES.get(null);
    }

    private static Field findLootTablesField() {
        try {
            Field field = LootTableHelper.class.getDeclaredField("lootTables");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void failPreload(Throwable throwable) {
        preloadPhase = PreloadPhase.FAILED;
        pendingLootTables = null;
        pendingResourceManager = null;
        pendingReload = null;
        VHAccelerator.LOGGER.warn(
                "JER menu preload failed; JER will use its original login-time path",
                throwable
        );
    }

    private static void logReuse() {
        VHAccelerator.LOGGER.debug("Reusing cached JER compatibility registries");
    }

    public enum PreloadPhase {
        NOT_STARTED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public record PreloadStatus(
            PreloadPhase phase,
            int percent,
            long elapsedMillis
    ) {
    }
}
