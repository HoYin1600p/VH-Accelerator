package dev.hoyin1600p.vhaccelerator.mixinmemory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LateSweepScheduleTest {
    @Test
    void neverFiresBeforeItIsArmed() {
        LateSweepSchedule schedule = new LateSweepSchedule(3);
        for (int i = 0; i < 10; i++) assertFalse(schedule.tick(true));
    }

    @Test
    void firesOnceOnTheLastEndTickAndIgnoresStartTicks() {
        LateSweepSchedule schedule = new LateSweepSchedule(3);
        schedule.arm();
        int fired = 0;
        int firedAtEndTick = -1;
        for (int tick = 1; tick <= 20; tick++) {
            assertFalse(schedule.tick(false), "START phase never counts");
            if (schedule.tick(true)) {
                fired++;
                firedAtEndTick = tick;
            }
        }
        assertEquals(1, fired);
        assertEquals(3, firedAtEndTick);
    }

    @Test
    void productionDelayIsFiveMinutesOfTicks() {
        assertEquals(6000, MixinTreeReleaseHook.LATE_SWEEP_TICKS);
        LateSweepSchedule schedule = new LateSweepSchedule(MixinTreeReleaseHook.LATE_SWEEP_TICKS);
        schedule.arm();
        for (int i = 1; i < 6000; i++) assertFalse(schedule.tick(true));
        assertTrue(schedule.tick(true));
        assertFalse(schedule.tick(true));
    }
}
