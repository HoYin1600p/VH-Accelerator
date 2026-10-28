package dev.hoyin1600p.vhaccelerator.compat.thermalfuels;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-manager state for building Thermal's converted-fuel list on first read instead of at every tag update.
 *
 * <p>{@code StirlingFuelManager} and {@code GourmandFuelManager} fill a {@code convertedFuels} list while the manager
 * refreshes, by creating an ItemStack for every registered item and asking Forge for its burn time or food value.
 * Only Thermal's JEI plugin ever reads the list, so on a dedicated server the work is never used. This keeps the
 * original method as the only code that builds the list: a refresh records that a build is owed, the first
 * {@code getConvertedFuels()} runs the original method once, and the next refresh (or {@code clear()}) throws the
 * owed build away. A reader therefore sees the same list the eager refresh would have produced.</p>
 *
 * <p>The lock is held between {@link #begin()} and {@link #end()}, so the original method's own re-entry (it is what
 * {@link #tryDefer} must let through) and any other thread are handled without extra flags.</p>
 */
public final class DeferredConvertedFuels<R> {
    private final ReentrantLock lock = new ReentrantLock();
    private R pending;
    private boolean owed;
    private boolean building;

    /** Called at the head of {@code createConvertedRecipes}; true means skip the body, a build is now owed. */
    public boolean tryDefer(R recipeManager) {
        lock.lock();
        try {
            if (building) return false;
            pending = recipeManager;
            owed = true;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Called at the head of {@code clear()}: the list is emptied, so an owed build is no longer wanted. */
    public void discard() {
        lock.lock();
        try {
            if (building) return;
            pending = null;
            owed = false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Called at the head of {@code getConvertedFuels()}. Returns the recipe manager of the owed build, with the lock
     * held until {@link #end()}, or null (lock released) when nothing is owed.
     */
    public R begin() {
        lock.lock();
        if (!owed || building) {
            lock.unlock();
            return null;
        }
        R manager = pending;
        pending = null;
        owed = false;
        building = true;
        return manager;
    }

    /** Must follow every non-null {@link #begin()}, in a finally block. */
    public void end() {
        building = false;
        lock.unlock();
    }

    /** True while a build is owed; for diagnostics and tests. */
    public boolean isOwed() {
        lock.lock();
        try {
            return owed;
        } finally {
            lock.unlock();
        }
    }
}
