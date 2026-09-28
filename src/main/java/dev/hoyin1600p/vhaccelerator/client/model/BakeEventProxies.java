package dev.hoyin1600p.vhaccelerator.client.model;

/**
 * Marks the model-bake-event dispatch, during which deferred block-state
 * lookups return {@link DeferredBakedModelProxy} stand-ins. VHA code that
 * needs real models inside the event (the CTM bake pass) suspends it.
 */
public final class BakeEventProxies {
    private static volatile boolean dispatching;
    private static final ThreadLocal<int[]> SUSPENDED = ThreadLocal.withInitial(() -> new int[1]);

    private BakeEventProxies() {
    }

    static void setDispatching(boolean value) {
        dispatching = value;
    }

    public static boolean enabled() {
        return dispatching && SUSPENDED.get()[0] == 0;
    }

    public static void suspend() {
        SUSPENDED.get()[0]++;
    }

    public static void resume() {
        SUSPENDED.get()[0]--;
    }
}
