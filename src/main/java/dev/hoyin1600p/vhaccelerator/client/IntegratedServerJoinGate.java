package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import io.netty.buffer.Unpooled;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Singleplayer join: after the integrated server places the local player it
 * would keep ticking the world while the client is still busy with the join
 * data it was sent (recipes, tags, JEI's start), competing with it for CPU.
 * The server now holds its world ticks, as if paused, until the client has
 * processed everything sent up to the player's placement: a marker packet
 * queued last comes back through the client thread. Chunk loading and
 * sending keep running (they are driven by the server's idle task loop, not
 * its ticks) and the network connection keeps being ticked. Only the local
 * player's first join is held, never a LAN or proxy connection, and never for
 * longer than {@link #MAX_HOLD_NANOS}. Adapted from ModernFix's
 * suspend_integrated_server_during_load (LGPL-3.0).
 */
public final class IntegratedServerJoinGate {
    public static final ResourceLocation MARKER = new ResourceLocation("vhaccelerator", "join_processed");
    private static final long MAX_HOLD_NANOS = 60_000_000_000L;
    /** Mods may rely on the first ticks for setup (ModernFix #639). */
    private static final int MIN_TICKS_BEFORE_HOLD = 2;

    private static WeakReference<MinecraftServer> heldServer = new WeakReference<>(null);
    private static volatile boolean holding;
    private static volatile long placedAt;

    private IntegratedServerJoinGate() {
    }

    /** Server thread, at the end of {@code PlayerList.placeNewPlayer}. */
    public static void playerPlaced(MinecraftServer server, Connection connection, ServerPlayer player) {
        if (!(server instanceof IntegratedServer)
                || !connection.isMemoryConnection()
                || heldServer.get() == server
                || !VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.VALUES.suspendIntegratedServerDuringJoin.get()) {
            return;
        }
        synchronized (IntegratedServerJoinGate.class) {
            heldServer = new WeakReference<>(server);
            placedAt = System.nanoTime();
            holding = true;
        }
        player.connection.send(new ClientboundCustomPayloadPacket(MARKER, new FriendlyByteBuf(Unpooled.buffer(0))));
    }

    /** Server thread, at the start of each integrated-server tick. */
    public static boolean holdTick(IntegratedServer server) {
        if (!holding || heldServer.get() != server || server.getTickCount() < MIN_TICKS_BEFORE_HOLD) {
            return false;
        }
        if (System.nanoTime() - placedAt > MAX_HOLD_NANOS) {
            release("the 60 s limit was reached");
            return false;
        }
        return true;
    }

    /** Network thread: releases on the client thread, after earlier packets. */
    public static void onMarker(Minecraft minecraft) {
        minecraft.executeIfPossible(() -> {
            if (minecraft.hasSingleplayerServer()) {
                release("the client processed its join data");
            }
        });
    }

    private static synchronized void release(String reason) {
        if (!holding) {
            return;
        }
        holding = false;
        VHAccelerator.LOGGER.info(
                "Integrated server resumed ticking {} ms after the player joined: {}",
                (System.nanoTime() - placedAt) / 1_000_000L,
                reason
        );
    }
}
