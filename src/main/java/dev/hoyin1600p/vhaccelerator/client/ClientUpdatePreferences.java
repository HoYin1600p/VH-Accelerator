package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.client.update.UpdateNoticeFilter;

/** Reads and changes the update-check options in the client config. */
final class ClientUpdatePreferences {
    private ClientUpdatePreferences() {
    }

    static boolean updateChecksEnabled(ClientConfigValues values) {
        return ClientLaunchSnapshot.launchValue(values.checkForUpdates, true);
    }

    static void setUpdateChecksEnabled(ClientConfigValues values, boolean enabled) {
        values.checkForUpdates.set(enabled);
        values.checkForUpdates.save();
    }

    static UpdateNoticeFilter updateNoticeFilter(ClientConfigValues values) {
        return ClientLaunchSnapshot.updateNoticeFilter(values);
    }

    static void setUpdateNoticeFilter(ClientConfigValues values, UpdateNoticeFilter filter) {
        values.updateNoticeFilter.set(filter);
        values.updateNoticeFilter.save();
    }
}
