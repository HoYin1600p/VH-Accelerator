package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JeiRuntimeEpochTest {
    @AfterEach void cleanup() { JeiRuntimeEpoch.invalidate(); }

    @Test void nativeRestartReplacesRuntimeBeforeNewWorkStarts() {
        AtomicLong token = new AtomicLong();
        Runnable start = JeiRuntimeEpoch.wrapStart(() -> token.set(JeiRuntimeEpoch.current()));
        start.run();
        long first = token.get();
        assertTrue(JeiRuntimeEpoch.isCurrent(first));
        JeiRuntimeEpoch.wrapStop(() -> assertFalse(JeiRuntimeEpoch.isCurrent(first))).run();
        start.run();
        assertFalse(JeiRuntimeEpoch.isCurrent(first));
        assertTrue(JeiRuntimeEpoch.isCurrent(token.get()));
    }

    @Test void failureAndConnectionTeardownInvalidatePendingPublication() {
        AtomicLong token = new AtomicLong();
        assertThrows(IllegalStateException.class, JeiRuntimeEpoch.wrapStart(() -> {
            token.set(JeiRuntimeEpoch.current());
            throw new IllegalStateException("plugin failed");
        })::run);
        assertFalse(JeiRuntimeEpoch.isCurrent(token.get()));
        JeiRuntimeEpoch.wrapStart(() -> token.set(JeiRuntimeEpoch.current())).run();
        JeiRuntimeEpoch.invalidate();
        assertFalse(JeiRuntimeEpoch.isCurrent(token.get()));
    }
}
