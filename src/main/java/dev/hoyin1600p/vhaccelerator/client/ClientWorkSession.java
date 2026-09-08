package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;

/**
 * Identifies client login/transfer work that belongs to the current server
 * session. Any callback holding an older generation must not publish into the
 * next world or into Minecraft after client teardown has begun.
 */
public final class ClientWorkSession {
    private static final ConnectionEpoch EPOCH = new ConnectionEpoch();

    private ClientWorkSession() {
    }

    public static synchronized boolean observeConnection(Object connection) {
        if (!EPOCH.observe(connection)) { return false; }
        DisconnectTimer.cancelActive();
        dev.hoyin1600p.vhaccelerator.client.compat.jei.JeiRuntimeEpoch.invalidate();
        long activeGeneration = EPOCH.current();
        PostLoginWorkTimer.beginSession(activeGeneration);
        if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            VHAccelerator.LOGGER.info(
                    "Client work session {} started",
                    activeGeneration
            );
        }
        return true;
    }

    public static synchronized long current() {
        return EPOCH.current();
    }

    public static synchronized boolean owns(Object connection) { return EPOCH.owns(connection); }

    public static synchronized boolean isCurrent(long generation) {
        return EPOCH.isCurrent(generation);
    }

    public static synchronized boolean invalidate(Object connection, String reason) {
        long invalidated = EPOCH.current();
        if (!EPOCH.close(connection)) { return false; }
        dev.hoyin1600p.vhaccelerator.client.compat.jei.JeiRuntimeEpoch.invalidate();
        PostLoginWorkTimer.cancelSession(invalidated);
        if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            VHAccelerator.LOGGER.info(
                    "Client work session {} invalidated at {}",
                    invalidated,
                    reason
            );
        }
        return true;
    }
}
