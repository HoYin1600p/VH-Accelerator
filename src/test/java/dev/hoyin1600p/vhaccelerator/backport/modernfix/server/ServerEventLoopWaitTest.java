package dev.hoyin1600p.vhaccelerator.backport.modernfix.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ServerEventLoopWaitTest {
    @Test
    void convertsTheRemainingMonotonicDeadlineToNanoseconds() {
        assertEquals(
                37_500_000L,
                ServerEventLoopWait.remainingNanos(
                        12_345L,
                        12_307_500_000L
                )
        );
    }

    @Test
    void reachedDeadlineNeverRequestsANegativePark() {
        assertEquals(
                0L,
                ServerEventLoopWait.remainingNanos(
                        12_345L,
                        12_345_000_000L
                )
        );
        assertEquals(
                0L,
                ServerEventLoopWait.remainingNanos(
                        12_345L,
                        12_400_000_000L
                )
        );
    }
}
