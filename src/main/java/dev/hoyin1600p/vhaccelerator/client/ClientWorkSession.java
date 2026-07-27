package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;

/**
 * Identifies client login/transfer work that belongs to the current server
 * session. Any callback holding an older generation must not publish into the
 * next world or into Minecraft after client teardown has begun.
 */
public final class ClientWorkSession {
    private static long nextGeneration;
    private static long activeGeneration = -1L;

    private ClientWorkSession() {
    }

    public static synchronized long begin() {
        DisconnectTimer.cancelActive();
        activeGeneration = ++nextGeneration;
        PostLoginWorkTimer.beginSession(activeGeneration);
        VHAccelerator.LOGGER.info(
                "Client work session {} started",
                activeGeneration
        );
        return activeGeneration;
    }

    public static synchronized long current() {
        return activeGeneration;
    }

    public static synchronized boolean isCurrent(long generation) {
        return generation >= 0L && generation == activeGeneration;
    }

    public static synchronized void invalidate(String reason) {
        long invalidated = activeGeneration;
        if (invalidated < 0L) {
            return;
        }

        activeGeneration = -1L;
        PostLoginWorkTimer.cancelSession(invalidated);
        VHAccelerator.LOGGER.info(
                "Client work session {} invalidated at {}",
                invalidated,
                reason
        );
    }
}
