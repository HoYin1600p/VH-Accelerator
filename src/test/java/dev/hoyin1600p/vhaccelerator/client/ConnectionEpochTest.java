package dev.hoyin1600p.vhaccelerator.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ConnectionEpochTest {
    @Test void observingSameConnectionPreservesStateThroughRespawnAndDimensionChange() {
        ConnectionEpoch epoch = new ConnectionEpoch();
        Object connection = new Object();
        assertTrue(epoch.observe(connection));
        long login = epoch.current();
        // Config handshake, game login, tags and recipes all use the same owner.
        for (int event = 0; event < 10; event++) {
            assertFalse(epoch.observe(connection));
            assertTrue(epoch.isCurrent(login));
        }
    }

    @Test void staleDisconnectAndPacketsCannotReplaceTheNewConnection() {
        ConnectionEpoch epoch = new ConnectionEpoch();
        Object old = new Object(), next = new Object();
        epoch.observe(old);
        long stale = epoch.current();
        assertTrue(epoch.observe(next));
        long active = epoch.current();
        assertFalse(epoch.isCurrent(stale));
        assertFalse(epoch.close(old));
        assertFalse(epoch.observe(old));
        assertTrue(epoch.isCurrent(active));
        assertTrue(epoch.owns(next));
        assertTrue(epoch.close(next));
        assertFalse(epoch.observe(next));
        assertFalse(epoch.isCurrent(active));
        assertEquals(-1, epoch.current());
    }

    @Test void ownershipUsesIdentityNotValueEquality() {
        ConnectionEpoch epoch = new ConnectionEpoch();
        String first = new String("connection"), second = new String("connection");
        epoch.observe(first);
        assertTrue(epoch.observe(second));
        assertFalse(epoch.close(first));
        assertTrue(epoch.owns(second));
    }
}
