package dev.hoyin1600p.vhaccelerator.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupRecorderTest {
    public static class StartedLike extends Event { }

    @TempDir Path temp;

    private final IEventBus bus = BusBuilder.builder().build();
    private final List<String> info = new ArrayList<>();
    private final List<String> warn = new ArrayList<>();

    private final StartupRecorder.Buses buses = new StartupRecorder.Buses() {
        @Override
        public void installModBuses(LifecycleListenerLedger ledger,
                                    java.util.function.Consumer<LifecycleListenerWrapping.Installation> sink) {
            if (failAfterFirst) {
                sink.accept(installOn(ledger));
                throw new IllegalStateException("second bus failed");
            }
        }

        @Override
        public void installForgeBus(LifecycleListenerLedger ledger,
                                    java.util.function.Consumer<LifecycleListenerWrapping.Installation> sink) {
            sink.accept(installOn(ledger));
        }

        @Override
        public Function<Class<?>, String> modOfClass() {
            return type -> "testmod";
        }
    };

    private boolean failAfterFirst;

    @Test
    void timersOffNeverObtainsOrTouchesEventBusesEvenWithJfrEnabled() {
        StartupRecorder off = new StartupRecorder(() -> false, () -> true,
                () -> { throw new AssertionError("timers off must not discover or mutate event buses"); },
                info::add, warn::add, System::nanoTime, null);
        off.onBeforeModLoading();
        off.onBeforeServerInit();
        assertFalse(off.onDeferOutput(report(), () -> { throw new AssertionError("no ledger"); }));
        off.onStartedReturned();
        assertTrue(info.isEmpty());
        assertTrue(warn.isEmpty());
    }

    private LifecycleListenerWrapping.Installation installOn(LifecycleListenerLedger ledger) {
        return LifecycleListenerWrapping.install(ledger, "forge", LifecycleListenerWrapping.busId(bus),
                List.of(StartedLike.class), listener -> new ListenerOwners(type -> "testmod").describe(listener));
    }

    @Test
    void failedInstallRestoresWhatWasWrappedAndStopsRecording() {
        List<String> calls = new ArrayList<>();
        bus.addListener((StartedLike e) -> calls.add("listener"));
        failAfterFirst = true;
        StartupRecorder on = recorder(true, false, null);
        on.onBeforeModLoading();
        assertFalse(on.onDeferOutput(report(), () -> { }), "a broken session never defers the report");
        bus.post(new StartedLike());
        on.onBeforeServerInit(); // no new session after a failure
        on.onStartedReturned();
        assertEquals(List.of("listener"), calls);
        assertTrue(info.isEmpty(), "nothing was timed, so nothing is logged");
        // The listener is the original again: a fresh recorder can wrap it and sees one invocation.
        failAfterFirst = false;
        StartupRecorder fresh = recorder(true, false, null);
        fresh.onBeforeServerInit();
        bus.post(new StartedLike());
        fresh.onStartedReturned();
        assertEquals(1, info.size());
        assertTrue(info.get(0).contains("1 listeners wrapped"));
    }

    @Test
    void startedHandlingRunsOnceEvenWhenTheHookFiresTwice() {
        StartupRecorder on = recorder(true, false, null);
        bus.addListener((StartedLike e) -> { });
        on.onBeforeServerInit();
        bus.post(new StartedLike());
        List<String> emitted = new ArrayList<>();
        assertTrue(on.onDeferOutput(report(), () -> emitted.add("report")));
        on.onStartedReturned();
        on.onStartedReturned();
        assertEquals(List.of("report"), emitted);
        assertEquals(1, info.size());
    }

    @Test
    void reportIsEmittedWhenAListenerThrowsAndTheHookRunsInFinally() {
        StartupRecorder on = recorder(true, false, null);
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGH, (StartedLike e) -> { });
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.LOW, (StartedLike e) -> {
            throw new IllegalStateException("listener failed");
        });
        on.onBeforeServerInit();
        List<String> emitted = new ArrayList<>();
        on.onDeferOutput(report(), () -> emitted.add("report"));
        try {
            bus.post(new StartedLike()); // what the mixin's try/finally wraps
        } catch (RuntimeException expected) {
            // The failure still propagates to the caller.
        } finally {
            on.onStartedReturned();
        }
        assertEquals(List.of("report"), emitted);
    }

    @Test
    void attributionPrefersModuleNameAndIgnoresOtherFileSystems(@TempDir Path dir) throws Exception {
        Map<String, String> modules = Map.of("securejar.cyclops", "cyclopscore");
        Map<Path, String> paths = Map.of(dir.resolve("mods/other.jar"), "othermod");
        assertEquals("cyclopscore", StartupRecorder.resolveMod("securejar.cyclops", modules, () -> null, paths));
        // A union/zip file system path never matches a default-file-system key and must not throw.
        Path zip = dir.resolve("union.zip");
        try (java.nio.file.FileSystem other = java.nio.file.FileSystems.newFileSystem(zip,
                Map.of("create", "true"))) {
            Path foreign = other.getPath("/mods/other.jar");
            assertNull(StartupRecorder.resolveMod(null, modules, () -> foreign, paths));
        }
        assertEquals("othermod", StartupRecorder.resolveMod("unknown.module", modules,
                () -> dir.resolve("mods/other.jar"), paths));
        assertEquals("othermod", StartupRecorder.resolveMod(null, modules,
                () -> dir.resolve("mods/other.jar/inner"), paths));
    }

    private StartupRecorder recorder(boolean timers, boolean jfr, StartupJfrRecorder.Backend backend) {
        return new StartupRecorder(() -> timers, () -> jfr, () -> buses, info::add, warn::add,
                System::nanoTime, backend);
    }

    private ServerStartupReport report() {
        long origin = System.nanoTime() - 1_000_000;
        return new ServerStartupReport(new ServerStartupReport.ClockMapping(
                1000, origin, origin + 100, "2026-10-24T00:00:00Z", 10, "test"), false);
    }

    @Test
    void ledgerOffMeansNothingIsWrappedDeferredOrLogged() {
        List<String> calls = new ArrayList<>();
        bus.addListener((StartedLike e) -> calls.add("listener"));
        StartupRecorder off = recorder(false, false, null);
        off.onBeforeModLoading();
        off.onBeforeServerInit();
        assertFalse(off.onDeferOutput(report(), () -> calls.add("never")));
        bus.post(new StartedLike());
        off.onStartedReturned();
        assertEquals(List.of("listener"), calls);
        assertTrue(info.isEmpty() && warn.isEmpty());
    }

    @Test
    void reportIsWrittenAfterTheLastListenerAndContainsTheSlowOne() throws Exception {
        List<String> order = new ArrayList<>();
        StartupRecorder on = recorder(true, false, null);
        ServerStartupReport report = report();
        List<Boolean> deferred = new ArrayList<>();
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGH, (StartedLike e) -> {
            // The VHA listener: the report is deferred, not written yet.
            deferred.add(on.onDeferOutput(report, () -> order.add("report written")));
            order.add("vha listener");
        });
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.LOW, (StartedLike e) -> {
            try {
                Thread.sleep(40);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            order.add("slow listener after vha");
        });
        on.onBeforeServerInit();
        bus.post(new StartedLike());
        order.add("post returned");
        on.onStartedReturned();

        assertEquals(List.of(true), deferred);
        assertEquals(List.of("vha listener", "slow listener after vha", "post returned", "report written"), order);
        JsonObject ledger = JsonParser.parseString(report.toJson()).getAsJsonObject()
                .getAsJsonObject("lifecycleListenerLedger");
        assertEquals(2, ledger.get("wrappedListeners").getAsInt());
        assertTrue(ledger.getAsJsonArray("slowest").get(0).getAsJsonObject()
                .get("durationMillis").getAsDouble() >= 30.0);
        assertEquals(1, info.size());
        assertTrue(info.get(0).startsWith("Lifecycle listener ledger:"));

        // Wrappers are gone: a second post is neither timed nor logged again.
        order.clear();
        bus.post(new StartedLike());
        assertEquals(2, order.size());
        assertEquals(1, info.size());
    }

    @Test
    void jfrFlagStartsRecordingAndTheStartedHookSchedulesItsStop() throws Exception {
        List<String> stopped = new ArrayList<>();
        StartupJfrRecorder.Backend backend = new StartupJfrRecorder.Backend() {
            @Override public boolean available() { return true; }

            @Override
            public StartupJfrRecorder.Handle start(String name, Path destination) {
                return () -> stopped.add(destination.getFileName().toString());
            }
        };
        StartupRecorder on = recorder(false, true, backend);
        on.onLaunchStarted("run-9", () -> temp);
        assertTrue(info.get(0).contains("startup-run-9.jfr"));
        on.onStartedReturned();
        // The real delay is 30 s; the scheduled stop is exercised by StartupJfrRecorderTest.stopLaterWrites...
        assertTrue(stopped.isEmpty(), "stop is deferred, not immediate");

        StartupRecorder disabled = recorder(false, false, backend);
        disabled.onLaunchStarted("run-10", () -> temp);
        assertTrue(stopped.isEmpty());
        assertEquals(1, info.size(), "jfr off starts nothing");
    }
}
