package dev.hoyin1600p.vhaccelerator.client.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateManifestParserTest {
    private static final String DOWNLOAD_URL =
            "https://www.curseforge.com/minecraft/mc-mods/vh-accelerator";

    @Test
    void parsesCriticalUpdateFromForgeManifestSchema() {
        String json = """
                {
                  "homepage": "%s",
                  "1.18.2": {
                    "1.0.13": "[CRITICAL] Critical Bug Fix"
                  },
                  "promos": {
                    "1.18.2-latest": "1.0.13",
                    "1.18.2-recommended": "1.0.13"
                  }
                }
                """.formatted(DOWNLOAD_URL);

        UpdateNotice notice = UpdateManifestParser.parse(
                json,
                "vhaccelerator",
                "VH Accelerator",
                "1.0.12",
                "1.18.2",
                DOWNLOAD_URL
        ).orElseThrow();

        assertEquals("1.0.13", notice.targetVersion());
        assertEquals(UpdateNotice.Severity.CRITICAL, notice.severity());
        assertEquals("Critical Bug Fix", notice.message());
    }

    @Test
    void ignoresManifestWhenClientIsCurrent() {
        String json = """
                {
                  "1.18.2": {"1.0.13": "Performance Improvement"},
                  "promos": {"1.18.2-latest": "1.0.13"}
                }
                """;

        assertTrue(UpdateManifestParser.parse(
                json,
                "vhaccelerator",
                "VH Accelerator",
                "1.0.13",
                "1.18.2",
                DOWNLOAD_URL
        ).isEmpty());
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(
                RuntimeException.class,
                () -> UpdateManifestParser.parse(
                        "not json",
                        "vhaccelerator",
                        "VH Accelerator",
                        "1.0.11",
                        "1.18.2",
                        DOWNLOAD_URL
                )
        );
    }

    @Test
    void rejectsAManifestWithoutTheCurrentMinecraftPromotion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UpdateManifestParser.parse(
                        "{\"promos\":{\"1.19-latest\":\"1.0.12\"}}",
                        "vhaccelerator",
                        "VH Accelerator",
                        "1.0.11",
                        "1.18.2",
                        DOWNLOAD_URL
                )
        );
    }
}
