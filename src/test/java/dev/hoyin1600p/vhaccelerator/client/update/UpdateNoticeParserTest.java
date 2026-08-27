package dev.hoyin1600p.vhaccelerator.client.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UpdateNoticeParserTest {
    private static final String DOWNLOAD_URL =
            "https://www.curseforge.com/minecraft/mc-mods/vh-accelerator";

    @Test
    void parsesCriticalTargetMessage() {
        String target = "1.0.12";
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put(
                target,
                "[CRITICAL] Critical Bug Fix"
        );
        UpdateNotice notice = UpdateNoticeParser.parse(
                "vhaccelerator",
                "1.0.11",
                target,
                changes,
                "VH Accelerator",
                DOWNLOAD_URL
        ).orElseThrow();

        assertEquals(UpdateNotice.Severity.CRITICAL, notice.severity());
        assertEquals("Critical Bug Fix", notice.message());
        assertEquals("1.0.12", notice.targetVersion());
    }

    @Test
    void treatsPlainChangelogAsNormalMessage() {
        UpdateNotice notice = UpdateNoticeParser.parse(
                "vhaccelerator",
                "1.0.11",
                "1.0.12",
                Map.of("1.0.12", "Performance Improvement"),
                "VH Accelerator",
                DOWNLOAD_URL
        ).orElseThrow();

        assertEquals(UpdateNotice.Severity.NORMAL, notice.severity());
        assertEquals("Performance Improvement", notice.message());
    }

    @Test
    void ignoresUpToDateResult() {
        assertTrue(UpdateNoticeParser.parse(
                "vhaccelerator",
                "1.0.12",
                "1.0.12",
                Map.of(),
                "VH Accelerator",
                DOWNLOAD_URL
        ).isEmpty());
    }

    @Test
    void stableReleaseSupersedesAPreRelease() {
        UpdateNotice notice = UpdateNoticeParser.parse(
                "vhaccelerator",
                "1.0.12-beta.1",
                "1.0.12",
                Map.of("1.0.12", "Stable Release"),
                "VH Accelerator",
                DOWNLOAD_URL
        ).orElseThrow();

        assertEquals("1.0.12", notice.targetVersion());
        assertEquals("Stable Release", notice.message());
    }
}
