package dev.hoyin1600p.vhaccelerator.client;

/** Identity-based ownership: screens, respawns and old disconnects are not connections. */
public final class ConnectionEpoch {
    private Object owner;
    private long sequence;
    private long active = -1;
    private final java.util.List<java.lang.ref.WeakReference<Object>> retired = new java.util.ArrayList<>();

    public synchronized boolean observe(Object connection) {
        if (connection == null || connection == owner) { return false; }
        retired.removeIf(reference -> reference.get() == null);
        if (retired.stream().anyMatch(reference -> reference.get() == connection)) { return false; }
        if (owner != null) { retired.add(new java.lang.ref.WeakReference<>(owner)); }
        owner = connection;
        active = ++sequence;
        return true;
    }

    public synchronized boolean close(Object connection) {
        if (connection == null || connection != owner) { return false; }
        retired.add(new java.lang.ref.WeakReference<>(owner));
        owner = null;
        active = -1;
        return true;
    }

    public synchronized long current() { return active; }
    public synchronized boolean owns(Object connection) { return connection != null && connection == owner; }
    public synchronized boolean isCurrent(long generation) {
        return generation >= 0 && generation == active;
    }
}
