package dev.hoyin1600p.vhaccelerator.client.bugreport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class BugReportScrubberTest {
    private final BugReportScrubber scrubber = new BugReportScrubber(
            "Notch_42", "069a79f4-44e9-4726-a5be-fca90e38aaf5", "Pat Example");

    @Test
    void windowsUserFoldersBecomeUserMarker() {
        assertEquals("<user>\\AppData\\Roaming\\.minecraft\\mods",
                scrubber.scrub("C:\\Users\\Someone\\AppData\\Roaming\\.minecraft\\mods"));
        assertEquals("at <user>/Desktop/pack", scrubber.scrub("at c:/users/Someone/Desktop/pack"));
        assertEquals("\"<user>\\\\AppData\"", scrubber.scrub("\"C:\\\\Users\\\\Someone\\\\AppData\""));
        assertEquals("home: <user>", scrubber.scrub("home: D:\\Users\\Someone"));
    }

    @Test
    void windowsNamesWithSpacesAreRemovedWhole() {
        assertEquals("<user>\\Games", scrubber.scrub("C:\\Users\\Jo Ann Smith\\Games"));
    }

    @Test
    void unixAndMacHomesBecomeUserMarker() {
        assertEquals("<user>/.minecraft/logs/latest.log", scrubber.scrub("/home/someone/.minecraft/logs/latest.log"));
        assertEquals("<user>/Library/Application Support",
                scrubber.scrub("/Users/someone/Library/Application Support"));
        assertEquals("https://example.com/Users/x", scrubber.scrub("https://example.com/Users/x"));
    }

    @Test
    void playerNameAndUuidAreReplaced() {
        assertEquals("Setting user <player> with uuid <uuid>",
                scrubber.scrub("Setting user Notch_42 with uuid 069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        assertEquals("session <uuid>", scrubber.scrub("session 069A79F444E94726A5BEFCA90E38AAF5"));
        assertEquals("<player> joined; Notch_421 did not", scrubber.scrub("notch_42 joined; Notch_421 did not"));
    }

    @Test
    void osAccountNameOutsidePathsIsReplaced() {
        assertEquals("user.name: <user>", scrubber.scrub("user.name: Pat Example"));
    }

    @Test
    void shortNamesAreLeftAloneButPathsStillScrub() {
        BugReportScrubber shortNames = new BugReportScrubber("Al", null, "Al");
        assertEquals("Also <user>\\x", shortNames.scrub("Also C:\\Users\\Al\\x"));
    }

    @Test
    void scrubbedCrashKeepsNoPersonalDetail() {
        String crash = "Description: Rendering entity\n\njava.lang.NullPointerException: boom\n"
                + "\tat C:\\Users\\Pat Example\\curseforge\\mod.jar\n"
                + "Player: Notch_42 (069a79f444e94726a5befca90e38aaf5)";
        String scrubbed = scrubber.scrub(crash);
        assertFalse(scrubbed.contains("Pat"));
        assertFalse(scrubbed.contains("Notch"));
        assertFalse(scrubbed.contains("069a79f4"));
    }
}
