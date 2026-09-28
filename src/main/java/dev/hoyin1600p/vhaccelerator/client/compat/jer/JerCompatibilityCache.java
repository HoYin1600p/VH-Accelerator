package dev.hoyin1600p.vhaccelerator.client.compat.jer;

import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;

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
import jeresources.entry.MobEntry;
import jeresources.util.LootTableHelper;
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
import net.minecraftforge.resource.ResourcePackLoader;

public final class JerCompatibilityCache {
    private static final Field JER_LOOT_TABLES = findLootTablesField();
    private static final Field MOB_ENTITY = findMobEntityField();

    private static boolean initialized;
    /*
     * JER reads loot tables from the integrated server when one exists, so
     * registries built in a singleplayer world describe that world's data
     * packs. Only registries built without an integrated server (JER's own
     * mod-pack loot tables) are reusable by a later session.
     */
    private static boolean initializedWithIntegratedServer;
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
                || !VHAcceleratorClientConfig.optimizationsEnabled()
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
        if (ModList.get().isLoaded("kubejs")) {
            VHAccelerator.LOGGER.debug(
                    "Deferred JER menu preload because KubeJS loot-table "
                            + "scripts require an active server context"
            );
            return;
        }

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
                boolean minecraftContainer = mod.requiredLanguageLoaders()
                        .stream()
                        .anyMatch(loader ->
                                "minecraft".equals(loader.languageName()));
                if (!minecraftContainer) {
                    packs.add(ResourcePackLoader.createPackForMod(mod));
                }
            }

            pendingResourceManager.registerReloadListener(pendingLootTables);
            pendingReload = pendingResourceManager.createReload(
                    SharedWorkers.compute(),
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
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.VALUES.cacheJerCompatibility.get()) {
            proxy.initCompatibility();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean integratedServer = minecraft.hasSingleplayerServer();
        if (initialized
                && !integratedServer
                && !initializedWithIntegratedServer) {
            logReuse();
            return;
        }

        if (!minecraft.isSameThread()) {
            throw new IllegalStateException("JER compatibility must be initialized on the client thread");
        }

        awaitMenuPreload(minecraft);
        long started = System.nanoTime();
        initialized = false;
        proxy.initCompatibility();
        initialized = true;
        initializedWithIntegratedServer = integratedServer;
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
        // Successful reload completion means no worker is using these pack
        // resources. Parsed loot tables do not own the resource manager.
        if (pendingResourceManager != null) {
            pendingResourceManager.close();
        }
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

    /**
     * Drops the display entities JER lazily creates for mob entries. They are
     * bound to the level that was loaded when first rendered, so a reused
     * registry would otherwise keep a disconnected world reachable. JER
     * recreates each entity from its supplier on the next render.
     */
    public static void releaseWorldReferences() {
        if (!initialized) {
            return;
        }
        if (initializedWithIntegratedServer || MOB_ENTITY == null) {
            // Not reusable, or entities cannot be released: rebuild next time.
            initialized = false;
            return;
        }
        try {
            for (MobEntry entry : MobRegistry.getInstance().getMobs()) {
                MOB_ENTITY.set(entry, null);
            }
        } catch (RuntimeException | IllegalAccessException exception) {
            initialized = false;
            VHAccelerator.LOGGER.warn(
                    "Unable to release JER mob display entities; JER "
                            + "compatibility will be rebuilt on the next login",
                    exception
            );
        }
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

    private static Field findMobEntityField() {
        try {
            Field field = MobEntry.class.getDeclaredField("entity");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            VHAccelerator.LOGGER.debug(
                    "JER mob entity field is unavailable; cached JER "
                            + "compatibility will not be reused across logins",
                    exception
            );
            return null;
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
