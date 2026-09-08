package dev.hoyin1600p.vhaccelerator.backport.modernfix.thread;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BackgroundWorkerLimitTest {
    @Test
    void usesAllJvmVisibleProcessors() {
        assertEquals(32, BackgroundWorkerLimit.recommendedWorkerCount(32));
        assertEquals(16, BackgroundWorkerLimit.recommendedWorkerCount(16));
        assertEquals(8, BackgroundWorkerLimit.recommendedWorkerCount(8));
    }

    @Test
    void alwaysLeavesAtLeastOneBackgroundWorker() {
        assertEquals(1, BackgroundWorkerLimit.recommendedWorkerCount(1));
        assertEquals(3, BackgroundWorkerLimit.recommendedWorkerCount(3));
        assertEquals(1, BackgroundWorkerLimit.recommendedWorkerCount(0));
    }
}
