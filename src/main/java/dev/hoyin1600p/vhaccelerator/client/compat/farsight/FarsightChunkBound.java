package dev.hoyin1600p.vhaccelerator.client.compat.farsight;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraftforge.event.TickEvent;

/**
 * Bounds the chunks Farsight 1.9 keeps resident on the client.
 *
 * <p>Farsight's only mixin cancels
 * {@code ClientPacketListener.handleForgetLevelChunk} at HEAD and redirects
 * the {@code chunkRadius()} / {@code getRadius()} reads in
 * {@code handleLogin} and {@code handleSetChunkCacheRadius} to its own
 * {@code maxchunkdist} config (default 32). The client therefore never runs a
 * chunk forget while it stays in one level: {@code ClientChunkCache.drop}
 * never fires, the light engine never releases a chunk's {@code DataLayer}s,
 * and Embeddium's unload hook (injected at RETURN of the cancelled method)
 * never removes the chunk's render sections. Every chunk seen while travelling
 * stays in memory until the level changes.
 *
 * <p>This class reproduces the vanilla forget for chunks the server has
 * already told the client to forget and that are farther than
 * {@code max(server radius, client render distance) + 1} from the player,
 * once every {@value #SWEEP_INTERVAL_TICKS} client ticks. Only a chunk the
 * server forgot may be dropped: the server resends a chunk only after it
 * forgot it, so dropping one it still counts as sent (the client runs ahead
 * of the server's view of the player, most of all when flying) leaves a hole
 * in the world that the server never refills until the player relogs. The real
 * server radius is recorded at HEAD of the two packet handlers, before
 * Farsight's redirect replaces the value the handler body sees. Chunks inside
 * the bound are untouched, so Farsight still shows terrain beyond the server
 * view distance up to the client's render distance.
 *
 * <p>Each eviction performs exactly what
 * {@code ClientPacketListener.handleForgetLevelChunk} does in 1.18.2:
 * {@link ClientChunkCache#drop(int, int)} (which posts Forge's
 * {@code ChunkEvent.Unload} and runs {@code ClientLevel.unload}), then a
 * queued light update that marks every section empty, disables the chunk's
 * light sources and calls {@code setLightReady}, followed by the Embeddium
 * notification that its own RETURN injection would have issued.
 *
 * <p>All state lives on the client thread. The tracking set is reset whenever
 * the client level changes or disappears, and nothing here runs on a
 * dedicated server because the class is only referenced from client code.
 */
public final class FarsightChunkBound {
    static final int SWEEP_INTERVAL_TICKS = 20;
    static final int BOUND_MARGIN = 1;

    /** Chunks the server forgot that are still resident client-side. */
    private static final LongOpenHashSet TRACKED = new LongOpenHashSet();
    private static WeakReference<ClientLevel> trackedLevel =
            new WeakReference<>(null);
    private static int serverChunkRadius;
    private static int ticksSinceSweep;
    private static long evictedTotal;

    private FarsightChunkBound() {
    }

    public static boolean enabled() {
        return VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.VALUES
                        .boundFarsightChunkRetention
                        .get();
    }

    /**
     * Records the radius the server actually sent. Called at HEAD of
     * {@code handleLogin} and {@code handleSetChunkCacheRadius}, where the
     * packet value is read directly rather than through the call Farsight
     * redirects inside the handler body.
     */
    public static void recordServerChunkRadius(int radius) {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        serverChunkRadius = Math.max(0, radius);
    }

    /**
     * The server resent a chunk, so it counts it as sent again and it must
     * stay. Called at RETURN of {@code handleLevelChunkWithLight}.
     */
    public static void onChunkLoaded(ClientLevel level, int chunkX, int chunkZ) {
        if (level == null || !Minecraft.getInstance().isSameThread()) {
            return;
        }
        syncLevel(level);
        TRACKED.remove(ChunkPos.asLong(chunkX, chunkZ));
    }

    /**
     * The server told the client to forget a chunk, which Farsight may have
     * kept. Called from the forget packet itself, which Farsight's cancel of
     * the handler does not reach; that cancel also runs before the handler
     * moves to the client thread, so this may arrive on the network thread
     * and is then queued behind the packets already scheduled on the client.
     */
    public static void onServerForget(int chunkX, int chunkZ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> onServerForget(chunkX, chunkZ));
            return;
        }
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        syncLevel(level);
        TRACKED.add(ChunkPos.asLong(chunkX, chunkZ));
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            reset();
            return;
        }
        syncLevel(level);
        if (!enabled()) {
            return;
        }
        if (++ticksSinceSweep < SWEEP_INTERVAL_TICKS) {
            return;
        }
        ticksSinceSweep = 0;
        sweep(minecraft, level);
    }

    static int bound(int serverRadius, int clientRenderDistance) {
        return Math.max(serverRadius, clientRenderDistance) + BOUND_MARGIN;
    }

    static boolean exceedsBound(
            int chunkX,
            int chunkZ,
            int centerX,
            int centerZ,
            int bound
    ) {
        return Math.abs(chunkX - centerX) > bound
                || Math.abs(chunkZ - centerZ) > bound;
    }

    private static void sweep(Minecraft minecraft, ClientLevel level) {
        if (TRACKED.isEmpty()) {
            return;
        }
        int bound = bound(serverChunkRadius, minecraft.options.renderDistance);
        ChunkPos center = minecraft.player.chunkPosition();
        LongArrayList evict = null;
        for (LongIterator iterator = TRACKED.iterator(); iterator.hasNext(); ) {
            long key = iterator.nextLong();
            if (exceedsBound(
                    ChunkPos.getX(key),
                    ChunkPos.getZ(key),
                    center.x,
                    center.z,
                    bound
            )) {
                if (evict == null) {
                    evict = new LongArrayList();
                }
                evict.add(key);
            }
        }
        if (evict == null) {
            return;
        }

        ClientChunkCache chunkCache = level.getChunkSource();
        for (int i = 0; i < evict.size(); i++) {
            long key = evict.getLong(i);
            TRACKED.remove(key);
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            // Farsight did not keep it, or it is gone already.
            if (chunkCache.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                continue;
            }
            forget(level, chunkCache, chunkX, chunkZ);
        }
        evictedTotal += evict.size();
        if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            VHAccelerator.LOGGER.info(
                    "Farsight bound: forgot {} chunk(s) beyond radius {} "
                            + "(server {}, render distance {}); {} tracked, "
                            + "{} forgotten this level",
                    evict.size(),
                    bound,
                    serverChunkRadius,
                    minecraft.options.renderDistance,
                    TRACKED.size(),
                    evictedTotal
            );
        }
    }

    /**
     * Mirrors {@code ClientPacketListener.handleForgetLevelChunk} and the
     * Embeddium injection that follows it. A chunk whose storage slot was
     * already reused, or that Embeddium no longer tracks, degrades to the same
     * no-ops vanilla performs for a late forget packet.
     */
    private static void forget(
            ClientLevel level,
            ClientChunkCache chunkCache,
            int chunkX,
            int chunkZ
    ) {
        chunkCache.drop(chunkX, chunkZ);
        level.queueLightUpdate(() -> {
            // The server may have resent it before this queued update ran.
            if (chunkCache.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null) {
                return;
            }
            LevelLightEngine lightEngine = level.getLightEngine();
            for (int section = level.getMinSection();
                    section < level.getMaxSection();
                    section++) {
                lightEngine.updateSectionStatus(
                        SectionPos.of(chunkX, section, chunkZ),
                        true
                );
            }
            lightEngine.enableLightSources(new ChunkPos(chunkX, chunkZ), false);
            level.setLightReady(chunkX, chunkZ);
        });
        EmbeddiumChunkUnloadBridge.onChunkRemoved(chunkX, chunkZ);
    }

    private static void syncLevel(ClientLevel level) {
        if (trackedLevel.get() == level) {
            return;
        }
        TRACKED.clear();
        trackedLevel = new WeakReference<>(level);
        ticksSinceSweep = 0;
        evictedTotal = 0;
    }

    private static void reset() {
        if (trackedLevel.get() == null && TRACKED.isEmpty()) {
            return;
        }
        TRACKED.clear();
        TRACKED.trim();
        trackedLevel = new WeakReference<>(null);
        ticksSinceSweep = 0;
        evictedTotal = 0;
    }
}
