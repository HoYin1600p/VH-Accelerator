package dev.hoyin1600p.vhaccelerator.backport.modernfix.watchdog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntegratedWatchdogTimingTest {
    @Test
    void identifiesThePreFirstTickSentinel() {
        assertTrue(IntegratedWatchdogTiming.serverIsBooting(-1L));
        assertFalse(IntegratedWatchdogTiming.serverIsBooting(0L));
    }

    @Test
    void replacesNonPositiveSleepWithAFullCheckInterval() {
        assertEquals(
                IntegratedWatchdogTiming.CHECK_INTERVAL_MILLIS,
                IntegratedWatchdogTiming.safeSleepDelay(-5_000L)
        );
        assertEquals(
                IntegratedWatchdogTiming.CHECK_INTERVAL_MILLIS,
                IntegratedWatchdogTiming.safeSleepDelay(0L)
        );
        assertEquals(
                12_345L,
                IntegratedWatchdogTiming.safeSleepDelay(12_345L)
        );
    }
}
