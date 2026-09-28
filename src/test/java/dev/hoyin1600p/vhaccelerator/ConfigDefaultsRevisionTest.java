package dev.hoyin1600p.vhaccelerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigDefaultsRevisionTest {
    @TempDir
    Path directory;

    @Test
    void movesOldDefaultsOnceAndKeepsLaterChoices() throws Exception {
        Path common = directory.resolve(ConfigMigration.COMMON_CONFIG);
        Files.writeString(common, "[backports]\n\tdeduplicateResourceLocationNamespaces = false\n");
        Path client = directory.resolve(ConfigMigration.CLIENT_CONFIG);
        Files.writeString(client, "[optimizations]\n\tdeferItemModelBaking = true\n\tskipBlockStateGraphLoading = false\n");

        ConfigDefaultsRevision.apply(directory);

        assertTrue(Files.readString(common).contains("deduplicateResourceLocationNamespaces = true"));
        assertTrue(Files.readString(client).contains("skipBlockStateGraphLoading = true"));
        assertTrue(Files.readString(client).contains("deferItemModelBaking = true"));
        assertEquals(ConfigDefaultsRevision.REVISION,
                ConfigDefaultsRevision.readRevision(directory.resolve(ConfigDefaultsRevision.MARKER)));

        // A player turning it off afterwards is respected.
        Files.writeString(common, "[backports]\n\tdeduplicateResourceLocationNamespaces = false\n");
        ConfigDefaultsRevision.apply(directory);
        assertTrue(Files.readString(common).contains("deduplicateResourceLocationNamespaces = false"));
    }

    @Test
    void freshInstallOnlyRecordsTheRevision() {
        ConfigDefaultsRevision.apply(directory);
        assertEquals(ConfigDefaultsRevision.REVISION,
                ConfigDefaultsRevision.readRevision(directory.resolve(ConfigDefaultsRevision.MARKER)));
    }
}
