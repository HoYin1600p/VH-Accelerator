package dev.hoyin1600p.vhaccelerator.client.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraftforge.fml.VersionChecker;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.junit.jupiter.api.Test;

class UpdateNoticeParserTest {
    private static final String DOWNLOAD_URL =
            "https://www.curseforge.com/minecraft/mc-mods/vh-accelerator";

    @Test
    void parsesCriticalTargetMessage() {
        ComparableVersion target = new ComparableVersion("1.0.12");
        Map<ComparableVersion, String> changes = new LinkedHashMap<>();
        changes.put(
                target,
                "[CRITICAL] Critical Bug Fix"
        );
        VersionChecker.CheckResult result = new VersionChecker.CheckResult(
                VersionChecker.Status.OUTDATED,
                target,
                changes,
                DOWNLOAD_URL
        );

        UpdateNotice notice = UpdateNoticeParser.parse(
                "vhaccelerator",
                result,
                "VH Accelerator",
                DOWNLOAD_URL
        ).orElseThrow();

        assertEquals(UpdateNotice.Severity.CRITICAL, notice.severity());
        assertEquals("Critical Bug Fix", notice.message());
        assertEquals("1.0.12", notice.targetVersion());
    }

    @Test
    void treatsPlainChangelogAsNormalMessage() {
        ComparableVersion target = new ComparableVersion("1.0.12");
        VersionChecker.CheckResult result = new VersionChecker.CheckResult(
                VersionChecker.Status.OUTDATED,
                target,
                Map.of(target, "Performance Improvement"),
                DOWNLOAD_URL
        );

        UpdateNotice notice = UpdateNoticeParser.parse(
                "vhaccelerator",
                result,
                "VH Accelerator",
                DOWNLOAD_URL
        ).orElseThrow();

        assertEquals(UpdateNotice.Severity.NORMAL, notice.severity());
        assertEquals("Performance Improvement", notice.message());
    }

    @Test
    void ignoresUpToDateResult() {
        VersionChecker.CheckResult result = new VersionChecker.CheckResult(
                VersionChecker.Status.UP_TO_DATE,
                null,
                Map.of(),
                DOWNLOAD_URL
        );

        assertTrue(UpdateNoticeParser.parse(
                "vhaccelerator",
                result,
                "VH Accelerator",
                DOWNLOAD_URL
        ).isEmpty());
    }
}
