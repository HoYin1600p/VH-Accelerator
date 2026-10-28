package dev.hoyin1600p.vhaccelerator.startup;

/** Common-side entry point for observational mixins. No optional-mod types are linked here. */
public final class StartupWorkObserver {
    private StartupWorkObserver() { }

    public static void enter(String operation, Object owner) {
        if (ServerLaunchTimer.detailedActive()) {
            ServerLaunchTimer.enterOperation(operation, owner == null ? "unknown"
                    : owner instanceof Class<?> type ? type.getName() : owner.getClass().getName());
        }
    }

    public static void exit(String operation) {
        ServerLaunchTimer.exitOperation(operation, null);
    }

    public static void generated(String operation, byte[] bytes) {
        ServerLaunchTimer.exitOperation(operation, bytes == null ? null : (long) bytes.length);
    }
}
