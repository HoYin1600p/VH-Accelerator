package dev.hoyin1600p.vhaccelerator.client.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateReminderStateTest {
    private static final String DOWNLOAD_URL =
            "https://www.curseforge.com/minecraft/mc-mods/vh-accelerator";

    @Test
    void normalNoticeAppearsImmediatelyThenAfterTenMoreJoins() {
        UpdateReminderState state = new UpdateReminderState();
        UpdateNotice notice = notice(
                "1.0.12",
                UpdateNotice.Severity.NORMAL,
                "Performance Improvement"
        );

        assertTrue(state.recordSuccessfulJoin(notice));
        for (int join = 1; join < 10; join++) {
            assertFalse(state.recordSuccessfulJoin(notice));
        }
        assertTrue(state.recordSuccessfulJoin(notice));
    }

    @Test
    void criticalNoticeAppearsImmediatelyThenAfterFiveMoreJoins() {
        UpdateReminderState state = new UpdateReminderState();
        UpdateNotice notice = notice(
                "1.0.12",
                UpdateNotice.Severity.CRITICAL,
                "Critical Bug Fix"
        );

        assertTrue(state.recordSuccessfulJoin(notice));
        for (int join = 1; join < 5; join++) {
            assertFalse(state.recordSuccessfulJoin(notice));
        }
        assertTrue(state.recordSuccessfulJoin(notice));
    }

    @Test
    void changedMessageResetsReminderImmediately() {
        UpdateReminderState state = new UpdateReminderState();
        UpdateNotice performance = notice(
                "1.0.12",
                UpdateNotice.Severity.NORMAL,
                "Performance Improvement"
        );
        UpdateNotice critical = notice(
                "1.0.12",
                UpdateNotice.Severity.CRITICAL,
                "Critical Bug Fix"
        );

        assertTrue(state.recordSuccessfulJoin(performance));
        assertFalse(state.recordSuccessfulJoin(performance));
        assertTrue(state.recordSuccessfulJoin(critical));
    }

    private static UpdateNotice notice(
            String version,
            UpdateNotice.Severity severity,
            String message
    ) {
        return new UpdateNotice(
                "vhaccelerator",
                "VH Accelerator",
                version,
                severity,
                message,
                DOWNLOAD_URL
        );
    }
}
