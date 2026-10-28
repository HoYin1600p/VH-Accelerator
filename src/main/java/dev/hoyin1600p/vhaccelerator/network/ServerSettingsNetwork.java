package dev.hoyin1600p.vhaccelerator.network;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.config.ForgeServerSettings;
import dev.hoyin1600p.vhaccelerator.config.ServerSettingsService;
import java.util.function.BiConsumer;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Optional play-only channel. No login packets or changes to the Forge handshake batcher. */
public final class ServerSettingsNetwork {
    public static final ResourceLocation NAME = new ResourceLocation(VHAccelerator.MOD_ID, "server_settings_v1");
    public static final String VERSION = "1";
    private static SimpleChannel channel;
    private static BiConsumer<Connection, ServerSettingsProtocol.Reply> receiver = (connection, reply) -> { };
    private ServerSettingsNetwork() { }

    /** Even an unknown protocol must not prevent a player joining; sending requires an exact version. */
    public static boolean acceptsRemote(String version) { return true; }
    public static boolean compatibleVersion(String version) { return VERSION.equals(version); }

    public static synchronized void register() {
        if (channel != null) return;
        channel = NetworkRegistry.newSimpleChannel(NAME, () -> VERSION,
                ServerSettingsNetwork::acceptsRemote, ServerSettingsNetwork::acceptsRemote);
        channel.messageBuilder(ServerSettingsProtocol.Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerSettingsProtocol::encodeRequest).decoder(ServerSettingsProtocol::decodeRequest)
                .consumer((request, contextSupplier) -> {
                    var context = contextSupplier.get();
                    context.enqueueWork(() -> {
                        var player = context.getSender();
                        if (player == null || !context.getNetworkManager().isConnected()
                                || !compatible(context.getNetworkManager())) return;
                        ServerSettingsService.Result result;
                        try {
                            // Construct the store only after authorization, so denied requests read no values.
                            result = !player.hasPermissions(4) ? ServerSettingsService.Result.failure(ServerSettingsService.Status.DENIED)
                                    : ServerSettingsService.handle(() -> player.hasPermissions(4),
                                            player.getGameProfile().getName(), request.changes(), new ForgeServerSettings());
                        } catch (RuntimeException failure) {
                            VHAccelerator.LOGGER.warn("Server settings request failed", failure);
                            result = ServerSettingsService.Result.failure(ServerSettingsService.Status.SAVE_FAILED);
                        }
                        channel.sendTo(new ServerSettingsProtocol.Reply(request.id(), result),
                                context.getNetworkManager(), NetworkDirection.PLAY_TO_CLIENT);
                    });
                    context.setPacketHandled(true);
                }).add();
        channel.messageBuilder(ServerSettingsProtocol.Reply.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerSettingsProtocol::encodeReply).decoder(ServerSettingsProtocol::decodeReply)
                .consumer((reply, contextSupplier) -> {
                    var context = contextSupplier.get();
                    context.enqueueWork(() -> receiver.accept(context.getNetworkManager(), reply));
                    context.setPacketHandled(true);
                }).add();
    }

    public static boolean compatible(Connection connection) {
        if (channel == null || connection == null) return false;
        var data = NetworkHooks.getConnectionData(connection);
        return data != null && compatibleVersion(data.getChannels().get(NAME)) && channel.isRemotePresent(connection);
    }

    public static void receiveOnClient(BiConsumer<Connection, ServerSettingsProtocol.Reply> callback) {
        receiver = callback;
    }

    public static void request(Connection connection, ServerSettingsProtocol.Request request) {
        if (!compatible(connection)) throw new IllegalStateException("Server settings channel unavailable");
        channel.sendTo(request, connection, NetworkDirection.PLAY_TO_SERVER);
    }
}
