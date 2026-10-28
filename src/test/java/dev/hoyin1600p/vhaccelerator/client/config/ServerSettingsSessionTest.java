package dev.hoyin1600p.vhaccelerator.client.config;

import static org.junit.jupiter.api.Assertions.*;
import dev.hoyin1600p.vhaccelerator.config.ServerSettingsService;
import dev.hoyin1600p.vhaccelerator.network.ServerSettingsProtocol;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServerSettingsSessionTest {
    private static final ServerSettingsService.Result SAVED = new ServerSettingsService.Result(ServerSettingsService.Status.OK,
            Map.of("optimizations.flag", new ServerSettingsService.Row(true, false, null, null, true)), List.of());

    @Test void onlyTheCurrentRequestCanPublishValues() {
        var session = new ServerSettingsSession(); Object connection = new Object();
        var results = new ArrayList<ServerSettingsService.Result>();
        var first = session.begin(connection, Map.of(), 0, results::add);
        var second = session.begin(connection, Map.of(), 1, results::add);
        assertFalse(session.accept(connection, new ServerSettingsProtocol.Reply(first.id(), SAVED)));
        assertTrue(results.isEmpty());
        assertTrue(session.accept(connection, new ServerSettingsProtocol.Reply(second.id(), SAVED)));
        assertEquals(List.of(SAVED), results); assertEquals(SAVED, session.snapshot(connection));
        assertFalse(session.accept(connection, new ServerSettingsProtocol.Reply(second.id(), SAVED)));
    }

    @Test void disconnectClearsValuesAndInvalidatesOldScreenTokens() {
        var session = new ServerSettingsSession(); Object connection = new Object(); Object old = session.token(connection);
        var request = session.begin(connection, Map.of(), 0, result -> { });
        assertTrue(session.accept(connection, new ServerSettingsProtocol.Reply(request.id(), SAVED)));
        session.tick(null, 1);
        assertTrue(session.snapshot(null).values().isEmpty()); assertNotSame(old, session.token(null));
        assertFalse(session.accept(null, new ServerSettingsProtocol.Reply(request.id(), SAVED)));
    }

    @Test void aReplyOnAnotherConnectionCannotPopulateTheSnapshot() {
        var session = new ServerSettingsSession(); Object first = new Object(), second = new Object();
        var results = new ArrayList<ServerSettingsService.Result>();
        var request = session.begin(first, Map.of(), 0, results::add);
        assertFalse(session.accept(second, new ServerSettingsProtocol.Reply(request.id(), SAVED)));
        assertTrue(session.snapshot(second).values().isEmpty());
        assertEquals(1, results.size()); assertEquals(ServerSettingsService.Status.UNAVAILABLE, results.get(0).status());
    }

    @Test void loginResetAlsoRejectsWorkWhenAProxyReusesTheConnection() {
        var session = new ServerSettingsSession(); Object connection = new Object(); Object old = session.token(connection);
        var results = new ArrayList<ServerSettingsService.Result>();
        var request = session.begin(connection, Map.of(), 0, results::add);
        session.reset(connection);
        assertNotSame(old, session.token(connection)); assertTrue(session.snapshot(connection).values().isEmpty());
        assertEquals(1, results.size()); assertEquals(ServerSettingsService.Status.UNAVAILABLE, results.get(0).status());
        assertFalse(session.accept(connection, new ServerSettingsProtocol.Reply(request.id(), SAVED)));
    }

    @Test void timeoutEndsWaitingOnceAndNeverClaimsAnUnconfirmedSaveSucceeded() {
        var session = new ServerSettingsSession(); Object connection = new Object();
        var results = new ArrayList<ServerSettingsService.Result>();
        var request = session.begin(connection, Map.of("optimizations.flag", true), 10, results::add);
        session.tick(connection, 5_000_000_009L); assertTrue(results.isEmpty());
        session.tick(connection, 5_000_000_010L); session.tick(connection, 6_000_000_000L);
        assertEquals(1, results.size()); assertEquals(ServerSettingsService.Status.UNAVAILABLE, results.get(0).status());
        assertTrue(session.snapshot(connection).values().isEmpty());
        assertFalse(session.accept(connection, new ServerSettingsProtocol.Reply(request.id(), SAVED)));
    }
}
