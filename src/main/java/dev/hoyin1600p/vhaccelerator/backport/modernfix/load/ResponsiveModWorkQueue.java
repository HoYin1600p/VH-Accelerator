/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/load/ModWorkManagerQueue.java
 * Upstream commit: 90fed2813ce7120e1c0667d3390cc1396118cc3a
 * Earlier upstream commit: 5ffac3bc3efe61a4727d307a1d45a33dbbf193ef
 * Original copyright: Copyright (c) 2023-2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; took the 250 microsecond park from the 1.20 line, kept
 * the loading-screen kick that Forge 40's drive loop still needs but paced it
 * by time instead of every other poll, moved queued tasks in order, and added
 * debug-only statistics.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.load;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import net.minecraftforge.fml.ModWorkManager;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * The main-thread task queue that Forge drives while it waits for each
 * mod-loading stage.
 *
 * <p>Forge 40 waits with {@code while (!done) drive(ticker)}, and
 * {@code drive} runs {@code while (driveOne()) ticker.run()}. On its own the
 * empty queue makes that a busy spin; ModernFix's 1.18 queue instead parks
 * 25 ms per empty poll, so the main thread notices a finished stage or a new
 * main-thread task up to 25-50 ms late, dozens of times per launch. This
 * queue parks 250 microseconds, as ModernFix's current line does. Because the
 * ticker (the loading-screen redraw) only runs after a task, an empty queue
 * returns a no-op task about every {@link #TICKER_INTERVAL_NANOS}, which keeps
 * the loading screen redrawing at ModernFix's former cadence.</p>
 */
public final class ResponsiveModWorkQueue extends ConcurrentLinkedDeque<Runnable> {
    private static final long PARK_NANOS = TimeUnit.MICROSECONDS.toNanos(250);
    static final long TICKER_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final Runnable TICKER_KICK = () -> {
    };
    private static final LongAdder EMPTY_POLLS = new LongAdder();
    private static final LongAdder TASKS = new LongAdder();

    private long lastKickNanos = System.nanoTime();

    @Override
    public Runnable pollFirst() {
        Runnable task = super.pollFirst();
        if (task != null) {
            TASKS.increment();
            return task;
        }
        EMPTY_POLLS.increment();
        LockSupport.parkNanos(PARK_NANOS);
        long now = System.nanoTime();
        if (now - lastKickNanos >= TICKER_INTERVAL_NANOS) {
            lastKickNanos = now;
            return TICKER_KICK;
        }
        return null;
    }

    /**
     * Replaces Forge's sync-executor queue (or the one ModernFix installed at
     * bootstrap), keeping any queued tasks in order.
     *
     * @return whether this queue is installed
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean install() {
        Class<?> syncExecutor;
        try {
            syncExecutor = Class.forName("net.minecraftforge.fml.ModWorkManager$SyncExecutor");
        } catch (ClassNotFoundException missing) {
            return false;
        }
        Object executor = ModWorkManager.syncExecutor();
        ConcurrentLinkedDeque<Runnable> current = (ConcurrentLinkedDeque<Runnable>)
                ObfuscationReflectionHelper.getPrivateValue((Class) syncExecutor, executor, "tasks");
        if (current instanceof ResponsiveModWorkQueue) {
            return true;
        }
        ResponsiveModWorkQueue replacement = new ResponsiveModWorkQueue();
        // Iterate rather than poll: ModernFix's queue parks and may return
        // a no-op task when polled empty.
        if (current != null) {
            for (Runnable task : current) {
                replacement.addLast(task);
            }
        }
        ObfuscationReflectionHelper.setPrivateValue((Class) syncExecutor, executor, replacement, "tasks");
        return true;
    }

    /** One debug line: tasks run and empty polls while waiting. */
    public static String describe() {
        return TASKS.sum() + " main-thread tasks, " + EMPTY_POLLS.sum() + " empty polls";
    }
}
