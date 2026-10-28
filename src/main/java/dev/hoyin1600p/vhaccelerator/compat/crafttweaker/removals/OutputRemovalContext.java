package dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals;

/** Version-neutral marker also read by the older client-only removal hook. */
public final class OutputRemovalContext {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUSPENDED = new ThreadLocal<>();

    private OutputRemovalContext() { }

    public static boolean active() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    static boolean indexAllowed() {
        return !Boolean.TRUE.equals(SUSPENDED.get());
    }

    static boolean suspend(boolean suspend) {
        boolean previous = !indexAllowed();
        if (suspend || previous) SUSPENDED.set(true);
        return previous;
    }

    static void restoreSuspension(boolean previous) {
        if (previous) SUSPENDED.set(true);
        else SUSPENDED.remove();
    }

    static boolean enter(boolean active) {
        boolean previous = active();
        if (active) ACTIVE.set(true);
        else ACTIVE.remove();
        return previous;
    }

    static void restore(boolean previous) {
        if (previous) ACTIVE.set(true);
        else ACTIVE.remove();
    }
}
