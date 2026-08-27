package dev.hoyin1600p.vhaccelerator.client.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateNoticeSessionTest {
    @Test
    void launchCanOnlyBeClaimedAfterTheFirstPlayableFrame() {
        UpdateNoticeSession session = new UpdateNoticeSession();

        assertFalse(session.claimEligibleLaunch());
        session.markPlayerLoggedIn();
        assertTrue(session.needsPlayableFrame());
        assertFalse(session.claimEligibleLaunch());

        session.markPlayableFrame();
        assertTrue(session.claimEligibleLaunch());
        assertFalse(session.claimEligibleLaunch());
    }

    @Test
    void laterWorldJoinsAndServerTransfersDoNotCountAgain() {
        UpdateNoticeSession session = new UpdateNoticeSession();

        session.markPlayerLoggedIn();
        session.markPlayableFrame();
        assertTrue(session.claimEligibleLaunch());

        session.markPlayerLoggedOut();
        session.markPlayerLoggedIn();
        session.markReceivingLevel();
        session.markPlayableFrame();

        assertFalse(session.claimEligibleLaunch());
    }

    @Test
    void armedReminderWaitsForAPlayableWorldAndDisplaysOnlyOnce() {
        UpdateNoticeSession session = new UpdateNoticeSession();

        session.armReminder(true);
        assertFalse(session.claimReminderDelivery());

        session.markPlayerLoggedIn();
        assertFalse(session.claimReminderDelivery());
        session.markPlayableFrame();
        assertTrue(session.claimReminderDelivery());

        session.markPlayerLoggedOut();
        session.markPlayerLoggedIn();
        session.markPlayableFrame();
        assertFalse(session.claimReminderDelivery());
    }

    @Test
    void disablingDropsAnArmedReminder() {
        UpdateNoticeSession session = new UpdateNoticeSession();

        session.markPlayerLoggedIn();
        session.markPlayableFrame();
        session.armReminder(true);
        session.disable();

        assertFalse(session.claimReminderDelivery());
    }

    @Test
    void playableRenderingRecoversAfterAnUnclassifiedTransferScreen() {
        UpdateNoticeSession session = new UpdateNoticeSession();

        session.markPlayerLoggedIn();
        session.markPlayableFrame();
        assertTrue(session.claimEligibleLaunch());

        session.markReceivingLevel();
        session.armReminder(true);
        assertTrue(session.needsPlayableFrame());
        assertFalse(session.claimReminderDelivery());

        session.markPlayableFrame();
        assertTrue(session.claimReminderDelivery());
        assertFalse(session.claimEligibleLaunch());
    }
}
