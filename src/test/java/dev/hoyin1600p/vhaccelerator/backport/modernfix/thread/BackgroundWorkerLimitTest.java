package dev.hoyin1600p.vhaccelerator.backport.modernfix.thread;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BackgroundWorkerLimitTest {
    @Test
    void reservesForegroundAndGcCapacityOnLargerSystems() {
        assertEquals(13, BackgroundWorkerLimit.recommendedWorkerCount(16));
        assertEquals(5, BackgroundWorkerLimit.recommendedWorkerCount(8));
    }

    @Test
    void alwaysLeavesAtLeastOneBackgroundWorker() {
        assertEquals(1, BackgroundWorkerLimit.recommendedWorkerCount(1));
        assertEquals(1, BackgroundWorkerLimit.recommendedWorkerCount(3));
        assertEquals(1, BackgroundWorkerLimit.recommendedWorkerCount(0));
    }
}
