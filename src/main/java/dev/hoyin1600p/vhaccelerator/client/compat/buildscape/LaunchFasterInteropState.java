package dev.hoyin1600p.vhaccelerator.client.compat.buildscape;

/**
 * Set by the mixin plugin when it applies VHA's answer to BuildScape's
 * {@code LaunchFasterInterop}. Without it BuildScape still runs its own
 * parallel model work, so VHA must keep leaving BuildScape models to it.
 */
public final class LaunchFasterInteropState {
    private static volatile boolean applied;

    private LaunchFasterInteropState() {
    }

    public static void markApplied() {
        applied = true;
    }

    public static boolean applied() {
        return applied;
    }
}
