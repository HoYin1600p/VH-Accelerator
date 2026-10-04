package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.client.update.UpdateNoticeFilter;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Entry point to the client config. The option definitions live in
 * {@link ClientConfigValues}, the launch-time snapshot in
 * {@link ClientLaunchSnapshot} and the update-check options in
 * {@link ClientUpdatePreferences}; this facade keeps one stable API for callers.
 */
public final class VHAcceleratorClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ClientConfigValues VALUES;

    static {
        Pair<ClientConfigValues, ForgeConfigSpec> pair =
                new ForgeConfigSpec.Builder().configure(ClientConfigValues::new);
        VALUES = pair.getLeft();
        SPEC = pair.getRight();
    }

    private VHAcceleratorClientConfig() {
    }

    public static synchronized void captureLaunchSnapshot() {
        ClientLaunchSnapshot.capture(VALUES);
    }

    public static boolean launchValue(ForgeConfigSpec.BooleanValue value) {
        return ClientLaunchSnapshot.launchValue(value);
    }

    public static boolean launchValue(
            ForgeConfigSpec.BooleanValue value,
            boolean defaultValue
    ) {
        return ClientLaunchSnapshot.launchValue(value, defaultValue);
    }

    public static boolean launchSnapshotCaptured() {
        return ClientLaunchSnapshot.captured();
    }

    public static boolean optimizationsEnabled() {
        return !VHAcceleratorConfig.compareModeEnabled()
                && launchValue(VALUES.enableClientOptimizations);
    }

    public static boolean updateChecksEnabled() {
        return ClientUpdatePreferences.updateChecksEnabled(VALUES);
    }

    public static void setUpdateChecksEnabled(boolean enabled) {
        ClientUpdatePreferences.setUpdateChecksEnabled(VALUES, enabled);
    }

    public static UpdateNoticeFilter updateNoticeFilter() {
        return ClientUpdatePreferences.updateNoticeFilter(VALUES);
    }

    public static void setUpdateNoticeFilter(UpdateNoticeFilter filter) {
        ClientUpdatePreferences.setUpdateNoticeFilter(VALUES, filter);
    }

    public static boolean launchProfilingEnabled() {
        return VHAcceleratorConfig.debugDiagnosticsEnabled()
                && launchValue(VALUES.profileClientLaunchPhases);
    }
}
