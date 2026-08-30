package dev.hoyin1600p.vhaccelerator.backport.modernfix.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ForgeHandshakeBatcherTest {
    @Test
    void drainsEveryImmediatelyProgressingPayloadInOneOuterTick() {
        AtomicInteger position = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();

        boolean done = ForgeHandshakeBatcher.tickUntilBlocked(
                position::get,
                () -> {
                    calls.incrementAndGet();
                    if (position.get() < 5) {
                        position.incrementAndGet();
                        return false;
                    }
                    return true;
                }
        );

        assertTrue(done);
        assertEquals(5, position.get());
        assertEquals(6, calls.get());
    }

    @Test
    void stopsAsSoonAsHandshakeWaitsWithoutPacketProgress() {
        AtomicInteger calls = new AtomicInteger();

        boolean done = ForgeHandshakeBatcher.tickUntilBlocked(
                () -> 3,
                () -> {
                    calls.incrementAndGet();
                    return false;
                }
        );

        assertFalse(done);
        assertEquals(1, calls.get());
    }

    @Test
    void completedHandshakeIsNeverReticked() {
        AtomicInteger calls = new AtomicInteger();

        boolean done = ForgeHandshakeBatcher.tickUntilBlocked(
                () -> 0,
                () -> {
                    calls.incrementAndGet();
                    return true;
                }
        );

        assertTrue(done);
        assertEquals(1, calls.get());
    }
}
