package dev.hoyin1600p.vhaccelerator.client.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateNoticeFilterTest {
    @Test
    void criticalFilterSuppressesNormalNotices() {
        assertFalse(UpdateNoticeFilter.CRITICAL.allows(notice(
                UpdateNotice.Severity.NORMAL
        )));
        assertTrue(UpdateNoticeFilter.CRITICAL.allows(notice(
                UpdateNotice.Severity.CRITICAL
        )));
    }

    @Test
    void allFilterAllowsBothSeverities() {
        assertTrue(UpdateNoticeFilter.ALL.allows(notice(
                UpdateNotice.Severity.NORMAL
        )));
        assertTrue(UpdateNoticeFilter.ALL.allows(notice(
                UpdateNotice.Severity.CRITICAL
        )));
    }

    @Test
    void configParsingIsCaseInsensitiveAndFailsClosed() {
        assertEquals(
                UpdateNoticeFilter.ALL,
                UpdateNoticeFilter.fromConfigValue(
                        "all",
                        UpdateNoticeFilter.CRITICAL
                )
        );
        assertEquals(
                UpdateNoticeFilter.CRITICAL,
                UpdateNoticeFilter.fromConfigValue(
                        "unknown",
                        UpdateNoticeFilter.CRITICAL
                )
        );
    }

    private static UpdateNotice notice(UpdateNotice.Severity severity) {
        return new UpdateNotice(
                "example",
                "Example",
                "2.0.0",
                severity,
                "Update",
                "https://www.curseforge.com/minecraft/mc-mods/example"
        );
    }
}
