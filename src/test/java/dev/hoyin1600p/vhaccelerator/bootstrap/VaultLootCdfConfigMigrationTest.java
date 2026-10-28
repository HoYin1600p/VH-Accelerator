package dev.hoyin1600p.vhaccelerator.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultLootCdfConfigMigrationTest {
    @TempDir Path directory;

    @Test
    void preservesDisabledClientPreferenceWithoutChangingTheClientFile() throws Exception {
        String original = "# keep this file\n[compatibility]\noptimizeVaultLootCdf = false\n";
        Path client = directory.resolve(ConfigMigration.CLIENT_CONFIG);
        Files.writeString(client, original);
        assertTrue(ConfigMigration.migrateVaultLootCdf(directory, true));
        assertTrue(Files.readString(directory.resolve(ConfigMigration.COMMON_CONFIG))
                .contains("optimizeVaultLootCdf = false"));
        assertEquals(original, Files.readString(client));
        assertFalse(ConfigMigration.migrateVaultLootCdf(directory, true));
    }

    @Test
    void explicitCommonPreferenceWinsAndOtherCommonSettingsSurvive() throws Exception {
        Files.writeString(directory.resolve(ConfigMigration.CLIENT_CONFIG),
                "[compatibility]\noptimizeVaultLootCdf = false\n");
        Path common = directory.resolve(ConfigMigration.COMMON_CONFIG);
        String original = "# authoritative\n[compatibility]\noptimizeVaultLootCdf = true\n";
        Files.writeString(common, original);
        assertFalse(ConfigMigration.migrateVaultLootCdf(directory, true));
        assertEquals(original, Files.readString(common));
    }

    @Test
    void missingClientPreferenceDoesNotWriteACommonConfig() throws Exception {
        assertFalse(ConfigMigration.migrateVaultLootCdf(directory, true));
        Files.writeString(directory.resolve(ConfigMigration.CLIENT_CONFIG), "[compatibility]\nother = false\n");
        assertFalse(ConfigMigration.migrateVaultLootCdf(directory, true));
        assertFalse(Files.exists(directory.resolve(ConfigMigration.COMMON_CONFIG)));
    }

    @Test
    void serverIgnoresAnyStrayClientConfiguration() throws Exception {
        Files.writeString(directory.resolve(ConfigMigration.CLIENT_CONFIG),
                "[compatibility]\noptimizeVaultLootCdf = false\n");
        assertFalse(ConfigMigration.migrateVaultLootCdf(directory, false));
        assertFalse(Files.exists(directory.resolve(ConfigMigration.COMMON_CONFIG)));
    }

    @Test
    void copiesIntoExistingCommonFileAndPreservesUnrelatedSettings() throws Exception {
        Files.writeString(directory.resolve(ConfigMigration.CLIENT_CONFIG),
                "[compatibility]\noptimizeVaultLootCdf = false\n");
        Files.writeString(directory.resolve(ConfigMigration.COMMON_CONFIG),
                "# existing comment\n[diagnostics]\ncompareMode = true\n");
        assertTrue(ConfigMigration.migrateVaultLootCdf(directory, true));
        String common = Files.readString(directory.resolve(ConfigMigration.COMMON_CONFIG));
        assertTrue(common.contains("existing comment"));
        assertTrue(common.contains("compareMode = true"));
        assertTrue(common.contains("optimizeVaultLootCdf = false"));
    }
}
