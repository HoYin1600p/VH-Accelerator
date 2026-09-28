package dev.hoyin1600p.vhaccelerator.compat.vaulthunters;

import iskallia.vault.core.vault.Modifiers;
import iskallia.vault.core.vault.modifier.spi.ModifierContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Vault Hunters' {@code Modifiers.tickServer} walks every modifier entry three
 * times each server tick: remove expired entries, apply new ones, count down
 * timed ones. A cake vault adds about 15 permanent entries per cake, so at 600
 * cakes that was about 27,000 entry visits per tick and a fifth of the server
 * thread, all of which did nothing. Only unapplied entries and timed entries
 * (with {@code TICKS_LEFT}) can do anything in those passes.
 *
 * <p>The index lists those entries and is rebuilt when the entry list changes
 * (identity, size, first or last entry), when an entry it treated as
 * permanent or that entry's context is written to (a command, Pandora or God
 * Altar expiring it, or a timer added to it), and every
 * {@link #REVALIDATE_TICKS} ticks. A tick with nothing to apply and nothing
 * expired only counts down the timed entries, in list order, as the original
 * third pass does; anything else runs the original method.
 */
public final class VaultModifierTicks {
    static final int REVALIDATE_TICKS = 200;
    private static final AtomicLong WATCHED_WRITES = new AtomicLong();

    private VaultModifierTicks() {
    }

    /** Implemented on every Vault data object; marks the ones the index relies on. */
    public interface Watchable {
        boolean vhaccelerator$isWatched();

        void vhaccelerator$setWatched(boolean watched);
    }

    /** Implemented on Modifiers to keep its index. */
    public interface Holder {
        Index vhaccelerator$tickIndex();

        void vhaccelerator$setTickIndex(Index index);
    }

    /** Called before a watched data object is written. */
    public static void watchedWrite() {
        WATCHED_WRITES.incrementAndGet();
    }

    /** True when this tick was handled; false means the original must run. */
    public static boolean tick(Holder holder, List<Modifiers.Entry> entries) {
        long writes = WATCHED_WRITES.get();
        int size = entries.size();
        Object first = size == 0 ? null : entries.get(0);
        Object last = size == 0 ? null : entries.get(size - 1);
        Index index = holder.vhaccelerator$tickIndex();
        if (index == null || index.entries != entries || index.size != size || index.first != first
                || index.last != last || index.writes != writes || ++index.age >= REVALIDATE_TICKS) {
            index = build(entries, size, first, last, writes);
            holder.vhaccelerator$setTickIndex(index);
        }
        if (index.unapplied) {
            holder.vhaccelerator$setTickIndex(null);
            return false;
        }
        for (Modifiers.Entry entry : index.active) {
            if (entry.hasExpired()) {
                holder.vhaccelerator$setTickIndex(null);
                return false;
            }
        }
        for (Modifiers.Entry entry : index.active) {
            entry.tick();
        }
        return true;
    }

    private static Index build(List<Modifiers.Entry> entries, int size, Object first, Object last, long writes) {
        List<Modifiers.Entry> active = new ArrayList<>();
        boolean unapplied = false;
        for (Modifiers.Entry entry : entries) {
            ModifierContext context = entry.getContext();
            boolean applied = entry.has(Modifiers.Entry.CONSUMED);
            boolean permanent = applied && context != null && !context.has(ModifierContext.TICKS_LEFT);
            unapplied |= !applied;
            ((Watchable) (Object) entry).vhaccelerator$setWatched(permanent);
            if (context != null) {
                ((Watchable) (Object) context).vhaccelerator$setWatched(permanent);
            }
            if (!permanent) {
                active.add(entry);
            }
        }
        return new Index(entries, size, first, last, writes, active.toArray(new Modifiers.Entry[0]), unapplied);
    }

    public static final class Index {
        final List<?> entries;
        final int size;
        final Object first;
        final Object last;
        final long writes;
        final Modifiers.Entry[] active;
        final boolean unapplied;
        int age;

        Index(List<?> entries, int size, Object first, Object last, long writes,
              Modifiers.Entry[] active, boolean unapplied) {
            this.entries = entries;
            this.size = size;
            this.first = first;
            this.last = last;
            this.writes = writes;
            this.active = active;
            this.unapplied = unapplied;
        }
    }
}
