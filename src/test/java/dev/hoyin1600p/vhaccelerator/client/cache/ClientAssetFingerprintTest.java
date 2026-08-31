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

    @Test
    void excludesDownloadedMetadataAndServerScopeIdentityOnly() {
        assertTrue(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "essential-mod-partner/data.cache.json"
        ));
        assertTrue(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "forgematica-server.properties"
        ));
        assertFalse(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "essential-client.toml"
        ));
        assertFalse(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "forgematica.json"
        ));
    }

    @Test
    void excludesPublicModUpdateReminderStateOnly() {
        assertTrue(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "vhaccelerator-update-notice-state.json"
        ));
        assertTrue(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "vault_render_optimization-update-notice-state.json"
        ));
        assertTrue(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "arcanebeam-update-notice-state.json"
        ));
        assertTrue(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "temporal_index-update-notice-state.json"
        ));
        assertTrue(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "forgematica-update-notice-state.json"
        ));
        assertFalse(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "vhaccelerator-client.toml"
        ));
        assertFalse(AssetConfigPathPolicy.isVolatileNonAssetConfig(
                "vault_render_optimization-client.toml"
        ));
    }
}
