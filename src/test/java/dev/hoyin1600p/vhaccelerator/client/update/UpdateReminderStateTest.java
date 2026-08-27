package dev.hoyin1600p.vhaccelerator.client.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateReminderStateTest {
    private static final String DOWNLOAD_URL =
            "https://www.curseforge.com/minecraft/mc-mods/vh-accelerator";

    @Test
    void normalNoticeAppearsOnEveryTenthEligibleLaunch() {
        UpdateReminderState state = new UpdateReminderState();
        UpdateNotice notice = notice(
                "1.0.12",
                UpdateNotice.Severity.NORMAL,
                "Performance Improvement"
        );

        for (int launch = 1; launch < 10; launch++) {
            assertFalse(state.recordEligibleLaunch(notice));
        }
        assertTrue(state.recordEligibleLaunch(notice));
        assertFalse(state.recordEligibleLaunch(notice));
    }

    @Test
    void criticalNoticeAppearsOnEveryFifthEligibleLaunch() {
        UpdateReminderState state = new UpdateReminderState();
        UpdateNotice notice = notice(
                "1.0.12",
                UpdateNotice.Severity.CRITICAL,
                "Critical Bug Fix"
        );

        for (int launch = 1; launch < 5; launch++) {
            assertFalse(state.recordEligibleLaunch(notice));
        }
        assertTrue(state.recordEligibleLaunch(notice));
        assertFalse(state.recordEligibleLaunch(notice));
    }

    @Test
    void changedMessageAndSeverityStartANewCadence() {
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

        for (int launch = 0; launch < 4; launch++) {
            assertFalse(state.recordEligibleLaunch(performance));
        }
        for (int launch = 0; launch < 4; launch++) {
            assertFalse(state.recordEligibleLaunch(critical));
        }
        assertTrue(state.recordEligibleLaunch(critical));
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
