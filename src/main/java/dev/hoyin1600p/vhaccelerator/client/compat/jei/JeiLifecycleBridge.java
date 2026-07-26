package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.util.Objects;

/**
 * Keeps common client lifecycle code independent from JEI's version-specific
 * internal classes. The active coordinator installs its callbacks when JEI
 * startup begins.
 */
public final class JeiLifecycleBridge {
    private static final Runnable NO_OP = () -> {
    };

    private static volatile Runnable disconnect = NO_OP;
    private static volatile Runnable recover = NO_OP;
    private static volatile String generation;

    private JeiLifecycleBridge() {
    }

    public static synchronized void install(
            String activeGeneration,
            Runnable disconnectHandler,
            Runnable recoverHandler
    ) {
        Objects.requireNonNull(activeGeneration, "activeGeneration");
        Objects.requireNonNull(disconnectHandler, "disconnectHandler");
        Objects.requireNonNull(recoverHandler, "recoverHandler");

        if (!activeGeneration.equals(generation)) {
            VHAccelerator.LOGGER.info(
                    "Selected {} compatibility module",
                    activeGeneration
            );
            generation = activeGeneration;
        }
        disconnect = disconnectHandler;
        recover = recoverHandler;
    }

    public static void onClientDisconnected() {
        disconnect.run();
    }

    public static void recoverAfterTransfer() {
        recover.run();
    }
}
