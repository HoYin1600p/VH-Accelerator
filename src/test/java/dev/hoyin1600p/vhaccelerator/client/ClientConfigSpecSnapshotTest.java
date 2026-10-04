package dev.hoyin1600p.vhaccelerator.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the client config spec: every value path, default, range, comment and
 * its declaration order, plus the TOML generated from the defaults. Change the
 * snapshot deliberately when a setting is added or reworded.
 */
class ClientConfigSpecSnapshotTest {
    @Test
    void specMatchesTheRecordedSnapshot() throws IOException {
        List<String> actual = ClientConfigSpecSnapshot.render();
        assertTrue(actual.size() > 100, "the spec should list every client option");
        try (InputStream stream = getClass().getResourceAsStream("client-config-spec-snapshot.txt")) {
            List<String> expected = List.of(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R"));
            assertEquals(expected, actual);
        }
    }
}
