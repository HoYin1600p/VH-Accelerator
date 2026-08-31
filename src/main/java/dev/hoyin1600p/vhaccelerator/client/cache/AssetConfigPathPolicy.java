package dev.hoyin1600p.vhaccelerator.client.cache;

import java.util.Locale;

final class AssetConfigPathPolicy {
    private AssetConfigPathPolicy() {
    }

    static boolean isVolatileNonAssetConfig(String relative) {
        String normalized = relative.toLowerCase(Locale.ROOT);
        return normalized.equals("distanthorizons.toml")
                || normalized.equals("embeddium-options.json")
                || normalized.equals("essential-mod-partner/data.cache.json")
                || normalized.equals("forge-client.toml")
                || normalized.equals("forgematica-server.properties")
                || normalized.equals("arcanebeam-update-notice-state.json")
                || normalized.equals("forgematica-update-notice-state.json")
                || normalized.equals("temporal_index-update-notice-state.json")
                || normalized.equals("vault_render_optimization-update-notice-state.json")
                || normalized.equals("vhaccelerator-update-notice-state.json")
                || normalized.equals("oculus.properties")
                || normalized.equals("powah.json5")
                || normalized.equals("reforgium-renderer.properties")
                || normalized.equals("sidebar_buttons.json")
                || normalized.equals("vaultlootbeams.json")
                || normalized.equals(
                        "modernstartupqol/startup_times.json"
                )
                || normalized.startsWith("xaerominimap")
                || normalized.startsWith("xaeroworldmap")
                || normalized.startsWith("voicechat/")
                || normalized.startsWith("byg/backups/")
                || normalized.startsWith("konkrete/locals/");
    }
}
