package dev.hoyin1600p.vhaccelerator.mixinmemory;

/** Counts server ticks down to the one follow-up class-tree sweep. Not thread-safe: used from the server thread. */
final class LateSweepSchedule {
    private final int delayTicks;
    private int remaining;

    LateSweepSchedule(int delayTicks) {
        this.delayTicks = delayTicks;
    }

    /** Starts (or restarts) the countdown. */
    void arm() {
        remaining = delayTicks;
    }

    /** Called on every server tick event; returns true exactly once, on the END tick that ends the countdown. */
    boolean tick(boolean endPhase) {
        if (remaining <= 0 || !endPhase) {
            return false;
        }
        return --remaining == 0;
    }
}
