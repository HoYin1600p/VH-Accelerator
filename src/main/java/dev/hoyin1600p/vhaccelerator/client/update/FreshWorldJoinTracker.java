package dev.hoyin1600p.vhaccelerator.client.update;

/**
 * Distinguishes a fresh menu-initiated connection from an in-connection world
 * replacement such as a proxy transfer or dimension change.
 */
final class FreshWorldJoinTracker {
    private boolean freshConnectionIntent = true;
    private boolean pendingFreshWorldJoin;

    void markFreshConnectionIntent() {
        freshConnectionIntent = true;
        pendingFreshWorldJoin = false;
    }

    void markPlayerLoggedIn() {
        if (!freshConnectionIntent) {
            return;
        }
        freshConnectionIntent = false;
        pendingFreshWorldJoin = true;
    }

    void markPlayerLoggedOut() {
        pendingFreshWorldJoin = false;
    }

    boolean isWaitingForPlayableFrame() {
        return pendingFreshWorldJoin;
    }

    boolean markFirstPlayableFrame() {
        if (!pendingFreshWorldJoin) {
            return false;
        }
        pendingFreshWorldJoin = false;
        return true;
    }
}
