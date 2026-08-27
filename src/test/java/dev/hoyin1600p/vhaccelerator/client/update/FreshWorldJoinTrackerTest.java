package dev.hoyin1600p.vhaccelerator.client.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FreshWorldJoinTrackerTest {
    @Test
    void countsFreshJoinOnlyAfterItsFirstPlayableFrame() {
        FreshWorldJoinTracker tracker = new FreshWorldJoinTracker();

        tracker.markFreshConnectionIntent();
        tracker.markPlayerLoggedIn();

        assertTrue(tracker.isWaitingForPlayableFrame());
        assertTrue(tracker.markFirstPlayableFrame());
        assertFalse(tracker.markFirstPlayableFrame());
    }

    @Test
    void ignoresServerTransferWithoutAMenuConnectionIntent() {
        FreshWorldJoinTracker tracker = new FreshWorldJoinTracker();
        tracker.markFreshConnectionIntent();
        tracker.markPlayerLoggedIn();
        assertTrue(tracker.markFirstPlayableFrame());

        tracker.markPlayerLoggedIn();

        assertFalse(tracker.isWaitingForPlayableFrame());
        assertFalse(tracker.markFirstPlayableFrame());
    }

    @Test
    void failedJoinDoesNotSurviveLogout() {
        FreshWorldJoinTracker tracker = new FreshWorldJoinTracker();
        tracker.markFreshConnectionIntent();
        tracker.markPlayerLoggedIn();

        tracker.markPlayerLoggedOut();

        assertFalse(tracker.markFirstPlayableFrame());
    }
}
