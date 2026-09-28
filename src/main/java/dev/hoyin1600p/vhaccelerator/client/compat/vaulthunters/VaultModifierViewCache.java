package dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Vault Hunters' HUD rebuilds the vault modifier list from every modifier
 * entry each frame ({@code Modifiers.getDisplayGroup}, {@code getModifiers}):
 * a registry lookup per entry. A cake vault adds about 15 entries per cake,
 * so after 420 cakes 56% of the render thread went there. On the server
 * thread, Vault's potion immunity check (and add-ons hooking it) call
 * {@code getModifiers} for every effect applied, several times a second per
 * player. On the client and integrated server threads the result is now
 * reused until the entry list changes (size, first or last entry) or
 * {@link #MAX_AGE_NANOS} passes, and every caller gets its own copy. Other
 * threads (world generation) always run the original code.
 */
public final class VaultModifierViewCache {
    static final long MAX_AGE_NANOS = 250_000_000L;
    private static final Map<Object, View> VIEWS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> COMPUTING = ThreadLocal.withInitial(() -> false);

    private VaultModifierViewCache() {
    }

    /** True while the original method runs to refill the cache. */
    public static boolean computing() {
        return COMPUTING.get();
    }

    public static Object2IntMap<Object> displayGroup(Object modifiers, List<?> entries,
                                                     Supplier<Object2IntMap<?>> original) {
        View view = view(modifiers, entries);
        if (view.displayGroup == null) {
            @SuppressWarnings("unchecked")
            Object2IntMap<Object> computed = (Object2IntMap<Object>) (Object2IntMap<?>) compute(original);
            view.displayGroup = new Object2IntOpenHashMap<>(computed);
        }
        return new Object2IntOpenHashMap<>(view.displayGroup);
    }

    public static List<Object> modifiers(Object modifiers, List<?> entries, Supplier<List<?>> original) {
        View view = view(modifiers, entries);
        if (view.modifiers == null) {
            view.modifiers = new ArrayList<>(compute(original));
        }
        return new ArrayList<>(view.modifiers);
    }

    private static <T> T compute(Supplier<T> original) {
        COMPUTING.set(true);
        try {
            return original.get();
        } finally {
            COMPUTING.set(false);
        }
    }

    private static View view(Object modifiers, List<?> entries) {
        int size = entries.size();
        Object first = size == 0 ? null : entries.get(0);
        Object last = size == 0 ? null : entries.get(size - 1);
        long now = System.nanoTime();
        synchronized (VIEWS) {
            View view = VIEWS.get(modifiers);
            if (view == null || view.entries != entries || view.size != size || view.first != first
                    || view.last != last || now - view.createdNanos > MAX_AGE_NANOS) {
                view = new View(entries, size, first, last, now);
                VIEWS.put(modifiers, view);
            }
            return view;
        }
    }

    private static final class View {
        final List<?> entries;
        final int size;
        final Object first;
        final Object last;
        final long createdNanos;
        Object2IntMap<Object> displayGroup;
        List<Object> modifiers;

        View(List<?> entries, int size, Object first, Object last, long createdNanos) {
            this.entries = entries;
            this.size = size;
            this.first = first;
            this.last = last;
            this.createdNanos = createdNanos;
        }
    }
}
