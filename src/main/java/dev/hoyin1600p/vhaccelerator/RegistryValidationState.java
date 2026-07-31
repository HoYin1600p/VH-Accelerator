package dev.hoyin1600p.vhaccelerator;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns mutable registry-validation state outside the transformed Forge class.
 * Normal JVM class initialization completes before either method can run.
 */
public final class RegistryValidationState {
    private static final AtomicInteger VALIDATION_CALLS = new AtomicInteger();

    private RegistryValidationState() {
    }

    public static boolean shouldSkipCurrentCall() {
        return VALIDATION_CALLS.incrementAndGet() % 3 != 0;
    }
}
