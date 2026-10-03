package dev.hoyin1600p.vhaccelerator.client.bugreport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Crash-report parsing and issue-link fitting. No network is involved anywhere in the feature. */
class BugReportPartsTest {
    private static final String CRASH = """
            ---- Minecraft Crash Report ----
            // Who set us up the TNT?

            Time: 02/10/2026, 12:00
            Description: Rendering entity in world

            java.lang.IllegalStateException: Buffer not started
            \tat net.minecraft.Foo.bar(Foo.java:1)
            """;

    @Test
    void exceptionLineFollowsTheDescription() {
        assertEquals("java.lang.IllegalStateException: Buffer not started", CrashReports.exceptionLine(CRASH));
        assertEquals("java.lang.OutOfMemoryError: Java heap space",
                CrashReports.exceptionLine("noise\njava.lang.OutOfMemoryError: Java heap space\n"));
        assertEquals("", CrashReports.exceptionLine("nothing here"));
    }

    @Test
    void newestCrashReportWins(@TempDir Path directory) throws IOException {
        Path older = Files.writeString(directory.resolve("crash-2026-01-01_client.txt"), "old");
        Path newer = Files.writeString(directory.resolve("crash-2026-02-01_client.txt"), "new");
        Files.writeString(directory.resolve("notes.log"), "ignored");
        Files.setLastModifiedTime(older, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(newer, FileTime.fromMillis(2_000));
        assertEquals(newer, CrashReports.newest(directory).orElseThrow());
        assertTrue(CrashReports.newest(directory.resolve("missing")).isEmpty());
    }

    @Test
    void issueLinkStaysUnderTheLimitAndKeepsTheCrashSection() {
        List<String> settings = new ArrayList<>();
        List<String> status = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            settings.add("general.setting_" + i + " = true");
            status.add("Status line " + i + " with some detail about the current state");
        }
        GitHubIssue.Report report = new GitHubIssue.Report("Bug: ", List.of("VH Accelerator: 1.0.0"),
                List.of("GPU: Test"), List.of("Mode: normal"), settings, status);

        String body = GitHubIssue.fittedBody(report, new GitHubIssue.Crash("java.lang.Error: x"));
        String url = GitHubIssue.url(report.title(), body);
        assertTrue(url.length() <= GitHubIssue.MAX_URL_LENGTH, "length " + url.length());
        assertTrue(body.contains("Crash report: paste it below (copied to your clipboard)."));
        assertTrue(body.contains("java.lang.Error: x"));
        assertTrue(url.startsWith(GitHubIssue.NEW_ISSUE_URL + "?title=Bug%3A%20&body="));
        assertFalse(url.contains("+"));
        String decoded = URLDecoder.decode(url.substring(url.indexOf("&body=") + 6), StandardCharsets.UTF_8);
        assertEquals(body, decoded);
    }

    @Test
    void smallReportIsNotTrimmed() {
        GitHubIssue.Report report = new GitHubIssue.Report("Bug: ", List.of("VH Accelerator: 1.0.0"),
                List.of("GPU: Test"), List.of("Mode: normal"), List.of("a.b = true"), List.of("Status: ACTIVE"));
        String body = GitHubIssue.fittedBody(report, null);
        assertTrue(body.contains("- `a.b = true`"));
        assertTrue(body.contains("Status: ACTIVE"));
        assertFalse(body.contains("Crash report"));
        assertFalse(body.contains("truncated"));
    }
}
