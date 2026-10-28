package dev.hoyin1600p.vhaccelerator.network;

import static org.junit.jupiter.api.Assertions.*;
import dev.hoyin1600p.vhaccelerator.config.ServerSettingsService;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkRegistry;
import org.junit.jupiter.api.Test;

class ServerSettingsProtocolTest {
    private static FriendlyByteBuf buffer() { return new FriendlyByteBuf(Unpooled.buffer()); }

    @Test void readChangeAndReplyRoundTripWithExactPrimitiveTypes() {
        var request = new ServerSettingsProtocol.Request(45, Map.of("a", true, "b", 8, "c", 0.25));
        var buffer = buffer();
        try {
            ServerSettingsProtocol.encodeRequest(request, buffer);
            assertEquals(request, ServerSettingsProtocol.decodeRequest(buffer));
        } finally { buffer.release(); }
        var reply = new ServerSettingsProtocol.Reply(45, new ServerSettingsService.Result(ServerSettingsService.Status.OK,
                Map.of("limits.count", new ServerSettingsService.Row(8, 5, 1.0, 10.0, true)), List.of("limits.count")));
        buffer = buffer();
        try {
            ServerSettingsProtocol.encodeReply(reply, buffer);
            assertEquals(reply, ServerSettingsProtocol.decodeReply(buffer));
        } finally { buffer.release(); }
    }

    @Test void oversizedDuplicateAndUnsupportedRequestsAreRejectedBeforeHandling() {
        for (int count : new int[] {-1, ServerSettingsProtocol.MAX_KEYS + 1}) {
            var buffer = buffer();
            try {
                buffer.writeLong(1); buffer.writeVarInt(count);
                assertThrows(IllegalArgumentException.class, () -> ServerSettingsProtocol.decodeRequest(buffer));
            } finally { buffer.release(); }
        }
        var duplicate = buffer();
        try {
            duplicate.writeLong(1); duplicate.writeVarInt(2);
            for (int i = 0; i < 2; i++) { duplicate.writeUtf("a"); duplicate.writeByte(0); duplicate.writeBoolean(true); }
            assertThrows(IllegalArgumentException.class, () -> ServerSettingsProtocol.decodeRequest(duplicate));
        } finally { duplicate.release(); }
        var badType = buffer();
        try {
            badType.writeLong(1); badType.writeVarInt(1); badType.writeUtf("a"); badType.writeByte(99);
            assertThrows(IllegalArgumentException.class, () -> ServerSettingsProtocol.decodeRequest(badType));
        } finally { badType.release(); }
    }

    @Test void absentVanillaOlderAndUnknownRemotesNeverBecomeAJoinRequirement() {
        for (String remote : List.of(NetworkRegistry.ABSENT, NetworkRegistry.ACCEPTVANILLA, "1", "0", "future"))
            assertTrue(ServerSettingsNetwork.acceptsRemote(remote));
        assertTrue(ServerSettingsNetwork.compatibleVersion("1"));
        for (String remote : List.of(NetworkRegistry.ABSENT, NetworkRegistry.ACCEPTVANILLA, "0", "future"))
            assertFalse(ServerSettingsNetwork.compatibleVersion(remote));
        assertFalse(ServerSettingsNetwork.compatibleVersion(null));
    }

    @Test void compiledNetworkAdapterUsesLevelFourAndOnlyQueuedPlayHandlers() throws Exception {
        try (var input = getClass().getResourceAsStream("ServerSettingsNetwork.class")) {
            assertNotNull(input);
            var node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(input.readAllBytes()).accept(node, 0);
            int permissionChecks = 0, queuedHandlers = 0;
            var directions = new java.util.HashSet<String>();
            for (var method : node.methods) for (var instruction : method.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call) {
                    if (call.name.equals("hasPermissions") && call.desc.equals("(I)Z")) {
                        var previous = instruction.getPrevious();
                        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
                        assertNotNull(previous); assertEquals(org.objectweb.asm.Opcodes.ICONST_4, previous.getOpcode());
                        permissionChecks++;
                    }
                    if (call.owner.equals("net/minecraftforge/network/NetworkEvent$Context") && call.name.equals("enqueueWork"))
                        queuedHandlers++;
                }
                if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
                        && field.owner.equals("net/minecraftforge/network/NetworkDirection")) directions.add(field.name);
            }
            assertEquals(2, permissionChecks); assertEquals(2, queuedHandlers);
            assertEquals(java.util.Set.of("PLAY_TO_CLIENT", "PLAY_TO_SERVER"), directions);
        }
    }
}
