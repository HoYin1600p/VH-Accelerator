package dev.hoyin1600p.vhaccelerator.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.junit.jupiter.api.Test;

/** Runs against the real EventBus 5.0.3 dispatch so ordering and attribution are the production ones. */
class LifecycleListenerWrappingTest {
    public static class SlowEvent extends Event { }

    /** Registered through reflection like an @Mod.EventBusSubscriber class. */
    public static class Subscriber {
        final List<String> calls;
        final long sleepMillis;

        Subscriber(List<String> calls, long sleepMillis) {
            this.calls = calls;
            this.sleepMillis = sleepMillis;
        }

        @SubscribeEvent
        public void onSlow(SlowEvent event) throws InterruptedException {
            Thread.sleep(sleepMillis);
            calls.add("subscriber");
        }
    }

    private static LifecycleListenerLedger.Described describe(net.minecraftforge.eventbus.api.IEventListener listener) {
        return new ListenerOwners(type -> type.getName().startsWith("dev.hoyin1600p") ? "testmod" : null)
                .describe(listener);
    }

    private static LifecycleListenerWrapping.Installation wrap(LifecycleListenerLedger ledger, IEventBus bus) {
        return LifecycleListenerWrapping.install(ledger, "test", LifecycleListenerWrapping.busId(bus),
                List.of(SlowEvent.class), LifecycleListenerWrappingTest::describe);
    }

    @Test
    void wrappingKeepsOrderPriorityAndSingleInvocationThenRestores() throws Exception {
        IEventBus bus = BusBuilder.builder().build();
        List<String> calls = new ArrayList<>();
        bus.addListener(EventPriority.LOW, (SlowEvent e) -> calls.add("low"));
        bus.addListener(EventPriority.HIGH, (SlowEvent e) -> calls.add("high-1"));
        bus.register(new Subscriber(calls, 30));
        bus.addListener(EventPriority.HIGH, (SlowEvent e) -> calls.add("high-2"));
        bus.addListener(EventPriority.LOWEST, (SlowEvent e) -> calls.add("lowest"));
        bus.post(new SlowEvent());
        List<String> expected = List.copyOf(calls);
        calls.clear();

        LifecycleListenerLedger ledger = new LifecycleListenerLedger(System.nanoTime());
        LifecycleListenerWrapping.Installation installation = wrap(ledger, bus);
        assertEquals(5, installation.wrappedListeners());
        bus.post(new SlowEvent());
        assertEquals(expected, calls);
        assertEquals(5, ledger.snapshot().invocations());

        // A second wrap pass finds nothing new to wrap and registers nothing twice.
        assertEquals(0, wrap(ledger, bus).wrappedListeners());
        calls.clear();
        bus.post(new SlowEvent());
        assertEquals(expected, calls);
        assertEquals(10, ledger.snapshot().invocations());

        assertEquals(5, installation.uninstall());
        calls.clear();
        bus.post(new SlowEvent());
        assertEquals(expected, calls);
        assertEquals(10, ledger.snapshot().invocations(), "restored listeners are no longer timed");
    }

    @Test
    void slowListenerIsNamedAndExceptionsStillPropagate() {
        IEventBus bus = BusBuilder.builder().setExceptionHandler((b, event, listeners, index, error) -> {
            throw new IllegalStateException("handled", error);
        }).build();
        List<String> calls = new ArrayList<>();
        bus.register(new Subscriber(calls, 40));
        bus.addListener((SlowEvent e) -> calls.add("fast"));
        LifecycleListenerLedger ledger = new LifecycleListenerLedger(System.nanoTime());
        wrap(ledger, bus);
        bus.post(new SlowEvent());

        LifecycleListenerLedger.Entry slowest = ledger.snapshot().slowest().get(0);
        assertEquals("testmod", slowest.mod());
        assertEquals(Subscriber.class.getName() + "#onSlow", slowest.listener());
        assertEquals("SlowEvent", slowest.eventType());
        assertTrue(slowest.durationNanos() >= 30_000_000L, "sleep is charged to the listener");
        assertEquals(Thread.currentThread().getName(), slowest.thread());

        IEventBus failing = BusBuilder.builder().build();
        failing.addListener((SlowEvent e) -> { throw new IllegalArgumentException("boom"); });
        LifecycleListenerLedger second = new LifecycleListenerLedger(System.nanoTime());
        wrap(second, failing);
        assertThrows(IllegalArgumentException.class, () -> failing.post(new SlowEvent()));
        assertEquals(1, second.snapshot().invocations(), "a failing listener is still recorded");
    }

    @Test
    void lambdaListenersAreAttributedToTheClassThatOwnsThem() {
        IEventBus bus = BusBuilder.builder().build();
        bus.addListener(this::lambdaOwner);
        LifecycleListenerLedger ledger = new LifecycleListenerLedger(System.nanoTime());
        wrap(ledger, bus);
        bus.post(new SlowEvent());
        LifecycleListenerLedger.Snapshot snapshot = ledger.snapshot();
        assertEquals(1, snapshot.invocations());
        assertEquals("testmod", snapshot.perMod().get(0).mod());
        // A zero-length call is not retained as a sample, so name the listener through the slot's mod total only.
        assertFalse(snapshot.slowest().stream().anyMatch(entry -> entry.listener().contains("EventBus")),
                "never the EventBus internals");
    }

    private void lambdaOwner(SlowEvent event) { }

    @Test
    void untouchedEventsAndBusesAreNeverAffected() {
        IEventBus wrapped = BusBuilder.builder().build();
        IEventBus other = BusBuilder.builder().build();
        List<String> calls = new ArrayList<>();
        wrapped.addListener((SlowEvent e) -> calls.add("wrapped"));
        other.addListener((SlowEvent e) -> calls.add("other"));
        LifecycleListenerLedger ledger = new LifecycleListenerLedger(System.nanoTime());
        wrap(ledger, wrapped);
        other.post(new SlowEvent());
        assertEquals(List.of("other"), calls);
        assertEquals(0, ledger.snapshot().invocations());
    }
}
