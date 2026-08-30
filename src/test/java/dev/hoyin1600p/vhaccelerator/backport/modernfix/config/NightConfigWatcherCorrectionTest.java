package dev.hoyin1600p.vhaccelerator.backport.modernfix.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NightConfigWatcherCorrectionTest {
    @Test
    void launchThreadNeverReceivesWatcherDelay() {
        Thread launchThread = Thread.currentThread();
        assertFalse(NightConfigWatcherCorrection.shouldDelay(
                launchThread,
                launchThread
        ));
    }

    @Test
    void aDifferentWatcherThreadReceivesTheDelay() {
        assertTrue(NightConfigWatcherCorrection.shouldDelay(
                Thread.currentThread(),
                new Thread()
        ));
    }
}
