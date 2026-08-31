package dev.hoyin1600p.vhaccelerator.client.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClientAssetFingerprintTest {
    @Test
    void excludesBygRollingBackupsWithoutIgnoringLiveConfiguration() {
        assertTrue(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "byg/backups/last_working_configs_backup.zip"
        ));
        assertTrue(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "BYG/BACKUPS/older.zip"
        ));
        assertFalse(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "byg/byg-client.toml"
        ));
        assertFalse(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "byg/byg-world.toml"
        ));
    }
}
