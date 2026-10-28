package dev.hoyin1600p.vhaccelerator.client.config;

import dev.hoyin1600p.vhaccelerator.config.ServerSettingsService;
import dev.hoyin1600p.vhaccelerator.network.ServerSettingsProtocol;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.function.Consumer;

/** Connection-independent screen token: retaining a closed screen never retains its network/world. */
public final class ServerSettingsSession {
    private static final long TIMEOUT_NANOS = 5_000_000_000L;
    private WeakReference<Object> connection = new WeakReference<>(null);
    private Object token = new Object();
    private long sequence;
    private Pending pending;
    private ServerSettingsService.Result snapshot = unavailable();
    private record Pending(long id, Object token, long deadline, Consumer<ServerSettingsService.Result> callback) { }

    public Object token(Object currentConnection) { synchronize(currentConnection); return token; }
    public ServerSettingsService.Result snapshot(Object currentConnection) { synchronize(currentConnection); return snapshot; }

    public void reset(Object currentConnection) {
        Pending old = pending;
        connection = new WeakReference<>(currentConnection);
        token = new Object(); pending = null; snapshot = unavailable();
        if (old != null) old.callback().accept(snapshot);
    }

    public ServerSettingsProtocol.Request begin(Object currentConnection, Map<String, Object> changes, long now,
                                                Consumer<ServerSettingsService.Result> callback) {
        synchronize(currentConnection);
        long id = ++sequence;
        pending = new Pending(id, token, now + TIMEOUT_NANOS, callback);
        return new ServerSettingsProtocol.Request(id, changes);
    }

    public boolean accept(Object currentConnection, ServerSettingsProtocol.Reply reply) {
        synchronize(currentConnection);
        Pending request = pending;
        if (request == null || request.token() != token || request.id() != reply.id()) return false;
        pending = null; snapshot = reply.result();
        request.callback().accept(snapshot);
        return true;
    }

    public void fail() {
        Pending request = pending;
        pending = null; snapshot = unavailable();
        if (request != null) request.callback().accept(snapshot);
    }

    public void tick(Object currentConnection, long now) {
        synchronize(currentConnection);
        if (pending != null && now - pending.deadline() >= 0) fail();
    }

    private void synchronize(Object currentConnection) {
        if (connection.get() != currentConnection) reset(currentConnection);
    }

    private static ServerSettingsService.Result unavailable() {
        return ServerSettingsService.Result.failure(ServerSettingsService.Status.UNAVAILABLE);
    }
}
