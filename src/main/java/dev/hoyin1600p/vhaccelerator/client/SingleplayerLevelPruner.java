package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCommonConfig;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * After leaving a singleplayer world, empties the big per-level structures of
 * the stopped integrated server and of the discarded client level.
 *
 * <p>Many mods keep a static reference to the last server, a server level, or
 * the last client level (Open Parties and Claims, FTB Backups, Copycats+,
 * Immersive Engineering, Mekanism, PneumaticCraft, Simple Voice Chat and
 * others), which keeps every loaded chunk, entity and scheduled tick in memory
 * until the next world. Emptying those structures leaves such a reference
 * holding a small shell. It runs only for an integrated server once it has
 * fully stopped, and for the client level only once the title screen is open
 * with no level and no connection; a proxy server transfer does neither.</p>
 */
public final class SingleplayerLevelPruner {
    private static volatile WeakReference<ClientLevel> leftLevel = new WeakReference<>(null);

    private SingleplayerLevelPruner() {
    }

    private static boolean enabled() {
        return BootstrapCommonConfig.bool(
                "compatibility", "releaseLevelPinningReferences", true);
    }

    /** Forge bus: integrated servers only, after saving and closing. */
    public static void onServerStopped(ServerStoppedEvent event) {
        MinecraftServer server = event.getServer();
        if (server == null || server.isDedicatedServer() || !enabled()) {
            return;
        }
        long started = System.nanoTime();
        int cleared = 0;
        for (ServerLevel level : server.getAllLevels()) {
            cleared += pruneServerLevel(level);
        }
        PlayerList players = server.getPlayerList();
        if (players != null) {
            cleared += clear(PlayerList.class, players, "f_11196_");
            cleared += clear(PlayerList.class, players, "f_11197_");
        }
        log("stopped integrated server", cleared, started);
    }

    /** Called when the client clears a level; remembers a singleplayer level to prune later. */
    public static void onClearLevel(Minecraft minecraft) {
        leftLevel = new WeakReference<>(
                minecraft.level != null && minecraft.hasSingleplayerServer() ? minecraft.level : null);
    }

    /** Called when a screen opens; prunes the left level once back at the title screen. */
    public static void onScreenOpened(Object screen) {
        if (!(screen instanceof TitleScreen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = leftLevel.get();
        if (level == null || minecraft.level != null || minecraft.getConnection() != null || !enabled()) {
            return;
        }
        leftLevel = new WeakReference<>(null);
        long started = System.nanoTime();
        int cleared = pruneClientLevel(level);
        log("left client level", cleared, started);
    }

    private static int pruneServerLevel(ServerLevel level) {
        int cleared = 0;
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        cleared += clear(ChunkMap.class, chunkMap, "f_140129_");
        cleared += clear(ChunkMap.class, chunkMap, "f_140130_");
        cleared += clear(ChunkMap.class, chunkMap, "f_140131_");
        cleared += clear(ServerLevel.class, level, "f_8546_");
        cleared += clear(ServerLevel.class, level, "f_143246_");
        cleared += clear(ServerLevel.class, level, "f_8556_");
        cleared += clear(ServerLevel.class, level, "f_143247_");
        cleared += clear(Level.class, level, "f_151512_");
        cleared += clear(Level.class, level, "f_151503_");
        Object manager = get(ServerLevel.class, level, "f_143244_");
        if (manager != null) {
            cleared += clear(PersistentEntitySectionManager.class, manager, "f_157491_");
            cleared += clear(PersistentEntitySectionManager.class, manager, "f_157497_");
            cleared += clear(PersistentEntitySectionManager.class, manager, "f_157498_");
            cleared += clear(PersistentEntitySectionManager.class, manager, "f_157500_");
            cleared += pruneLookup(get(PersistentEntitySectionManager.class, manager, "f_157494_"));
            cleared += pruneSections(get(PersistentEntitySectionManager.class, manager, "f_157495_"));
        }
        for (String ticks : new String[] {"f_8553_", "f_184047_"}) {
            Object levelTicks = get(ServerLevel.class, level, ticks);
            if (levelTicks != null) {
                for (String field : new String[] {"f_193202_", "f_193203_", "f_193204_", "f_193205_", "f_193206_", "f_193207_"}) {
                    cleared += clear(LevelTicks.class, levelTicks, field);
                }
            }
        }
        return cleared;
    }

    private static int pruneClientLevel(ClientLevel level) {
        int cleared = 0;
        cleared += clear(ClientLevel.class, level, "f_104566_");
        cleared += clear(Level.class, level, "f_151512_");
        cleared += clear(Level.class, level, "f_151503_");
        Object manager = get(ClientLevel.class, level, "f_171631_");
        if (manager != null) {
            cleared += pruneLookup(get(TransientEntitySectionManager.class, manager, "f_157637_"));
            cleared += pruneSections(get(TransientEntitySectionManager.class, manager, "f_157638_"));
            cleared += clear(TransientEntitySectionManager.class, manager, "f_157639_");
        }
        Object storage = get(ClientChunkCache.class, level.getChunkSource(), "f_104410_");
        if (storage != null) {
            Object chunks = get(storage.getClass(), storage, "f_104466_");
            if (chunks instanceof AtomicReferenceArray<?> array) {
                for (int i = 0; i < array.length(); i++) {
                    array.set(i, null);
                }
                cleared++;
            }
        }
        return cleared;
    }

    private static int pruneLookup(Object lookup) {
        return lookup == null ? 0
                : clear(EntityLookup.class, lookup, "f_156807_") + clear(EntityLookup.class, lookup, "f_156808_");
    }

    private static int pruneSections(Object sections) {
        return sections == null ? 0
                : clear(EntitySectionStorage.class, sections, "f_156852_") + clear(EntitySectionStorage.class, sections, "f_156853_");
    }

    private static Object get(Class<?> owner, Object instance, String srgName) {
        try {
            return ObfuscationReflectionHelper.getPrivateValue(castOwner(owner), instance, srgName);
        } catch (RuntimeException failure) {
            VHAccelerator.LOGGER.debug("Could not read {}.{} while releasing a left level", owner.getSimpleName(), srgName, failure);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> castOwner(Class<?> owner) {
        return (Class<T>) owner;
    }

    /** Empties a collection, map or queue field; returns 1 when it did. */
    private static int clear(Class<?> owner, Object instance, String srgName) {
        Object value = get(owner, instance, srgName);
        try {
            if (value instanceof Map<?, ?> map) {
                map.clear();
            } else if (value instanceof Collection<?> collection) {
                collection.clear();
            } else if (value instanceof it.unimi.dsi.fastutil.longs.Long2LongMap map) {
                map.clear();
            } else {
                return 0;
            }
            return 1;
        } catch (RuntimeException failure) {
            VHAccelerator.LOGGER.debug("Could not clear {}.{} while releasing a left level", owner.getSimpleName(), srgName, failure);
            return 0;
        }
    }

    private static void log(String what, int cleared, long started) {
        if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            VHAccelerator.LOGGER.info(
                    "[debug] Released the {}'s level structures ({} emptied) in {} ms",
                    what,
                    cleared,
                    (System.nanoTime() - started) / 1_000_000L
            );
        }
    }
}
