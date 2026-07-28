package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.cache.LoginStateFingerprint;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;

/**
 * Measures unusually large packet deserialization without retaining packet
 * buffers or changing Forge's packet ordering.
 */
public final class PacketDecodeProfiler {
    private static final int LARGE_PACKET_BYTES = 1_000_000;
    private static final int FINGERPRINT_PACKET_BYTES = 262_144;
    private static final ThreadLocal<Sample> ACTIVE = new ThreadLocal<>();

    private PacketDecodeProfiler() {
    }

    public static void begin(int packetId, FriendlyByteBuf buffer) {
        if (!ClientConnectionProfiler.isActive()
                || buffer.readableBytes() < FINGERPRINT_PACKET_BYTES) {
            ACTIVE.remove();
            return;
        }
        ACTIVE.set(new Sample(
                packetId,
                buffer.readableBytes(),
                System.nanoTime(),
                LoginStateFingerprint.fingerprintPayload(buffer)
        ));
    }

    public static void finish(Packet<?> packet) {
        Sample sample = ACTIVE.get();
        ACTIVE.remove();
        if (sample == null) {
            return;
        }
        if (packet instanceof ClientboundUpdateRecipesPacket) {
            LoginStateFingerprint.captureRecipePayloadHash(
                    sample.payloadHash()
            );
        }
        if (sample.bytes() < LARGE_PACKET_BYTES) {
            return;
        }
        long elapsed = Math.max(0L, System.nanoTime() - sample.startedNanos());
        VHAccelerator.LOGGER.info(
                "Large packet decode {} (id {}, {} bytes) completed in {} ms",
                packet == null ? "invalid" : packet.getClass().getName(),
                sample.packetId(),
                sample.bytes(),
                String.format(
                        java.util.Locale.ROOT,
                        "%.3f",
                        elapsed / 1_000_000.0
                )
        );
    }

    private record Sample(
            int packetId,
            int bytes,
            long startedNanos,
            String payloadHash
    ) {
    }
}
