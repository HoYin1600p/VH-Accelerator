package dev.hoyin1600p.vhaccelerator.client.config;

import dev.hoyin1600p.vhaccelerator.config.ServerSettingsService;
import dev.hoyin1600p.vhaccelerator.network.ServerSettingsNetwork;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Client-thread adapter; the independently tested session model owns replies, invalidation and timeout. */
public final class ServerSettingsClient {
    private static final ServerSettingsSession SESSION = new ServerSettingsSession();
    private static boolean initialized;
    private ServerSettingsClient() { }

    public static boolean connected() { return connection() != null; }
    public static Object session() { return SESSION.token(connection()); }
    public static ServerSettingsService.Result snapshot() { return SESSION.snapshot(connection()); }

    public static void requestFor(Object session, Map<String, Object> changes,
                                  Consumer<ServerSettingsService.Result> callback) {
        if (session != session()) { callback.accept(unavailable()); return; }
        request(changes, callback);
    }

    public static void request(Map<String, Object> changes, Consumer<ServerSettingsService.Result> callback) {
        initialize();
        Connection connection = connection();
        if (!ServerSettingsNetwork.compatible(connection)) {
            SESSION.reset(connection);
            callback.accept(unavailable());
            return;
        }
        var request = SESSION.begin(connection, changes, System.nanoTime(), callback);
        try { ServerSettingsNetwork.request(connection, request); }
        catch (RuntimeException unavailable) { SESSION.fail(); }
    }

    private static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerSettingsNetwork.receiveOnClient((connection, reply) -> {
            if (connection == connection()) SESSION.accept(connection, reply);
        });
        MinecraftForge.EVENT_BUS.addListener(ServerSettingsClient::tick);
        MinecraftForge.EVENT_BUS.addListener(ServerSettingsClient::joined);
        MinecraftForge.EVENT_BUS.addListener(ServerSettingsClient::left);
    }

    private static void joined(ClientPlayerNetworkEvent.LoggedInEvent event) { SESSION.reset(connection()); }
    private static void left(ClientPlayerNetworkEvent.LoggedOutEvent event) { SESSION.reset(null); }
    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) SESSION.tick(connection(), System.nanoTime());
    }
    private static Connection connection() {
        var listener = Minecraft.getInstance().getConnection();
        return listener == null ? null : listener.getConnection();
    }
    private static ServerSettingsService.Result unavailable() {
        return ServerSettingsService.Result.failure(ServerSettingsService.Status.UNAVAILABLE);
    }
}
