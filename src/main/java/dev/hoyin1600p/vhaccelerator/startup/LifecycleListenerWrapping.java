package dev.hoyin1600p.vhaccelerator.startup;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraftforge.eventbus.ListenerList;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventListenerHelper;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.IEventListener;

/**
 * Times individual EventBus listeners by interposing an {@link IEventListener} wrapper.
 *
 * <p>EventBus 5.0.3 lives in the boot layer, outside the transforming class loader, so it cannot be
 * mixed into. Its supported extension surface is the listener list: {@code EventBus.post} walks the
 * array returned by {@code ListenerList.getListeners(busId)} and calls each element. This class
 * swaps the public registration of each existing listener for a timing wrapper at a quiescent
 * point and swaps it back afterwards. Listener order is preserved: every listener the event class
 * owns is re-registered in its original sequence and priority. Listeners inherited from a
 * superclass's list are left alone.
 *
 * <p>Known limit: while wrapped, an {@code EventBus.unregister(Object)} for a wrapped listener
 * would not find it. These are startup-only windows on a dedicated server.
 */
final class LifecycleListenerWrapping {
    private static final Field BUS_ID = busIdField();

    private LifecycleListenerWrapping() { }

    /** The wrap points installed for one bus; {@link #uninstall()} restores the originals. */
    static final class Installation {
        private final String busLabel;
        private final int busId;
        private final List<Class<? extends Event>> events = new ArrayList<>();
        private int wrapped;

        private Installation(String busLabel, int busId) {
            this.busLabel = busLabel;
            this.busId = busId;
        }

        String busLabel() {
            return busLabel;
        }

        int wrappedListeners() {
            return wrapped;
        }

        /** Restores every wrapped listener of this installation. Returns the number restored. */
        int uninstall() {
            int restored = 0;
            for (Class<? extends Event> event : events) {
                restored += rewrite(event, busId, listener ->
                        listener instanceof TimedListener timed ? timed.delegate : listener);
            }
            events.clear();
            return restored;
        }
    }

    /** Wraps the listeners currently registered for the given event types on one bus. */
    static Installation install(LifecycleListenerLedger ledger, String busLabel, int busId,
                                Collection<Class<? extends Event>> eventTypes,
                                Function<IEventListener, LifecycleListenerLedger.Described> describe) {
        Installation installation = new Installation(busLabel, busId);
        try {
            installEvents(ledger, installation, eventTypes, describe);
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            installation.uninstall(); // never leave a partly wrapped bus behind
            throw failure;
        }
        return installation;
    }

    private static void installEvents(LifecycleListenerLedger ledger, Installation installation,
                                      Collection<Class<? extends Event>> eventTypes,
                                      Function<IEventListener, LifecycleListenerLedger.Described> describe) {
        int busId = installation.busId;
        for (Class<? extends Event> event : eventTypes) {
            String eventName = event.getSimpleName();
            int count = rewrite(event, busId, listener -> {
                if (listener instanceof TimedListener) return listener;
                LifecycleListenerLedger.Slot slot = ledger.register(eventName, () -> describe.apply(listener));
                if (slot == null) return listener;
                return new TimedListener(listener, ledger, slot);
            });
            installation.wrapped += count;
            installation.events.add(event);
        }
    }

    /**
     * Replaces the owned listeners of one event class through the public ListenerList API.
     *
     * @return how many listeners the mapper replaced
     */
    private static int rewrite(Class<? extends Event> event, int busId,
                               Function<IEventListener, IEventListener> mapper) {
        ListenerList list = EventListenerHelper.getListenerList(event);
        IEventListener[] current = list.getListeners(busId);
        Set<IEventListener> inherited = Collections.newSetFromMap(new IdentityHashMap<>());
        Class<?> parentType = event.getSuperclass();
        if (parentType != null && Event.class.isAssignableFrom(parentType)) {
            @SuppressWarnings("unchecked")
            Class<? extends Event> parentEvent = (Class<? extends Event>) parentType;
            Collections.addAll(inherited, EventListenerHelper.getListenerList(parentEvent).getListeners(busId));
        }
        List<IEventListener> originals = new ArrayList<>();
        List<IEventListener> replacements = new ArrayList<>();
        List<EventPriority> priorities = new ArrayList<>();
        EventPriority priority = EventPriority.NORMAL;
        int replaced = 0;
        for (IEventListener listener : current) {
            if (listener instanceof EventPriority marker) {
                priority = marker;
                continue;
            }
            if (inherited.contains(listener)) continue;
            IEventListener replacement = mapper.apply(listener);
            if (replacement != listener) replaced++;
            originals.add(listener);
            replacements.add(replacement);
            priorities.add(priority);
        }
        if (replaced == 0) return 0;
        // Re-register every owned listener in sequence so relative order never changes.
        for (IEventListener original : originals) list.unregister(busId, original);
        for (int i = 0; i < originals.size(); i++) {
            try {
                list.register(busId, priorities.get(i), replacements.get(i));
            } catch (RuntimeException | LinkageError failure) {
                // Never lose a listener: fall back to the original registration.
                list.register(busId, priorities.get(i), originals.get(i));
                StartupDiagnostics.failure("lifecycle.rewrite", failure);
            }
        }
        return replaced;
    }

    /** Reads the private bus id that {@code EventBus.post} uses to select its listener array. */
    static int busId(IEventBus bus) {
        if (BUS_ID == null) throw new IllegalStateException("EventBus bus id is not accessible");
        try {
            return BUS_ID.getInt(bus);
        } catch (IllegalAccessException | IllegalArgumentException failure) {
            throw new IllegalStateException("EventBus bus id is not readable", failure);
        }
    }

    private static Field busIdField() {
        try {
            Field field = Class.forName("net.minecraftforge.eventbus.EventBus").getDeclaredField("busID");
            return field.trySetAccessible() ? field : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return null;
        }
    }

    /** Forwards to the real listener and charges its wall time to a ledger slot. */
    static final class TimedListener implements IEventListener {
        private final IEventListener delegate;
        private final LifecycleListenerLedger ledger;
        private final LifecycleListenerLedger.Slot slot;

        TimedListener(IEventListener delegate, LifecycleListenerLedger ledger,
                      LifecycleListenerLedger.Slot slot) {
            this.delegate = delegate;
            this.ledger = ledger;
            this.slot = slot;
        }

        IEventListener delegate() {
            return delegate;
        }

        @Override
        public void invoke(Event event) {
            long start = System.nanoTime();
            try {
                delegate.invoke(event);
            } finally {
                try {
                    ledger.record(slot, Thread.currentThread().getName(), start, System.nanoTime());
                } catch (RuntimeException | LinkageError | AssertionError failure) {
                    StartupDiagnostics.failure("lifecycle.record", failure);
                }
            }
        }

        @Override
        public String listenerName() {
            return delegate.listenerName();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
