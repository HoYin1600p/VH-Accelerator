package dev.hoyin1600p.vhaccelerator.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hoyin1600p.vhaccelerator.startup.LifecycleListenerLedger.Described;
import dev.hoyin1600p.vhaccelerator.startup.LifecycleListenerLedger.Slot;
import java.util.List;
import org.junit.jupiter.api.Test;

class LifecycleListenerLedgerTest {
    private static final long MS = 1_000_000L;
    private static final long ORIGIN = 5_000_000_000L;

    private static Slot slot(LifecycleListenerLedger ledger, String event, String mod, String listener) {
        return ledger.register(event, () -> new Described(mod, listener));
    }

    @Test
    void slowestListenersAreOrderedAndAttributedToTheirMods() {
        LifecycleListenerLedger ledger = new LifecycleListenerLedger(ORIGIN);
        Slot cyclops = slot(ledger, "ServerStartedEvent", "cyclopscore", "org.cyclops.Infobook#onStarted");
        Slot observable = slot(ledger, "ServerStartedEvent", "observable", "dev.ob.Hook#onStarted");
        Slot tiny = slot(ledger, "ServerStartingEvent", "tiny", "t.T#go");
        ledger.record(observable, "Server thread", ORIGIN + 100 * MS, ORIGIN + 124 * MS);
        ledger.record(cyclops, "Server thread", ORIGIN + 200 * MS, ORIGIN + 200 * MS + 13_200 * MS);
        ledger.record(tiny, "Server thread", ORIGIN + 50 * MS, ORIGIN + 51 * MS);
        ledger.record(cyclops, "Worker-1", ORIGIN + 14_000 * MS, ORIGIN + 14_005 * MS);

        LifecycleListenerLedger.Snapshot snapshot = ledger.snapshot();

        assertEquals(3, snapshot.wrappedListeners());
        assertEquals(4, snapshot.invocations());
        List<LifecycleListenerLedger.Entry> slowest = snapshot.slowest();
        assertEquals(List.of("cyclopscore", "observable", "cyclopscore", "tiny"),
                slowest.stream().map(LifecycleListenerLedger.Entry::mod).toList());
        assertEquals(13_200 * MS, slowest.get(0).durationNanos());
        assertEquals(200 * MS, slowest.get(0).startOffsetNanos());
        assertEquals("Server thread", slowest.get(0).thread());
        assertEquals("ServerStartedEvent", slowest.get(0).eventType());
        assertEquals("org.cyclops.Infobook#onStarted", slowest.get(0).listener());
        assertEquals("Worker-1", slowest.get(2).thread());

        LifecycleListenerLedger.ModTotal top = snapshot.perMod().get(0);
        assertEquals("cyclopscore", top.mod());
        assertEquals(2, top.invocations());
        assertEquals(13_205 * MS, top.totalNanos());
        assertEquals(13_200 * MS, top.maxNanos());
    }

    @Test
    void retainedSamplesAreBoundedAndKeepTheSlowest() {
        LifecycleListenerLedger ledger = new LifecycleListenerLedger(ORIGIN, 3, 10);
        Slot slot = slot(ledger, "E", "m", "l");
        for (int ms = 1; ms <= 100; ms++) {
            ledger.record(slot, "t", ORIGIN, ORIGIN + ms * MS);
        }
        LifecycleListenerLedger.Snapshot snapshot = ledger.snapshot();
        assertEquals(100, snapshot.invocations());
        assertEquals(List.of(100 * MS, 99 * MS, 98 * MS),
                snapshot.slowest().stream().map(LifecycleListenerLedger.Entry::durationNanos).toList());
    }

    @Test
    void slotBudgetIsBoundedAndRefusalsAreCounted() {
        LifecycleListenerLedger ledger = new LifecycleListenerLedger(ORIGIN, 4, 2);
        assertTrue(slot(ledger, "E", "m", "a") != null);
        assertTrue(slot(ledger, "E", "m", "b") != null);
        assertEquals(null, slot(ledger, "E", "m", "c"));
        LifecycleListenerLedger.Snapshot snapshot = ledger.snapshot();
        assertEquals(2, snapshot.wrappedListeners());
        assertEquals(1, snapshot.refusedListeners());
    }

    @Test
    void perModTotalsAreCappedAndAFailingResolverNamesTheListenerUnknown() {
        LifecycleListenerLedger ledger = new LifecycleListenerLedger(ORIGIN, 4, 1000);
        for (int i = 0; i < LifecycleListenerLedger.MOD_LIMIT + 5; i++) {
            Slot slot = slot(ledger, "E", "mod" + i, "l" + i);
            ledger.record(slot, "t", ORIGIN, ORIGIN + (i + 1) * MS);
        }
        Slot broken = ledger.register("E", () -> { throw new IllegalStateException("no metadata"); });
        ledger.record(broken, "t", ORIGIN, ORIGIN + 1);
        LifecycleListenerLedger.Snapshot snapshot = ledger.snapshot();
        assertEquals(LifecycleListenerLedger.MOD_LIMIT, snapshot.perMod().size());
        assertTrue(snapshot.modsTruncated());
        assertEquals("mod68", snapshot.perMod().get(0).mod());
    }

    @Test
    void reportAndLogBlockCarryTheTopTenWithoutUnboundedRows() {
        LifecycleListenerLedger ledger = new LifecycleListenerLedger(ORIGIN);
        for (int i = 1; i <= 15; i++) {
            Slot slot = slot(ledger, "ServerStartedEvent", "mod" + i, "pkg.Mod" + i + "#onStarted");
            ledger.record(slot, "Server thread", ORIGIN + i * MS, ORIGIN + i * MS + i * 100 * MS);
        }
        LifecycleListenerLedger.Snapshot snapshot = ledger.snapshot();
        String block = LifecycleListenerLedger.logBlock(snapshot, 10);
        System.out.println(block);
        String[] lines = block.split("\\R");
        assertEquals(11, lines.length);
        assertTrue(lines[0].contains("15 listeners wrapped"));
        assertTrue(lines[1].contains("mod15") && lines[1].contains("1500.0 ms")
                && lines[1].contains("ServerStartedEvent") && lines[1].contains("pkg.Mod15#onStarted")
                && lines[1].contains("[Server thread]"));
        assertTrue(lines[10].contains("mod6"));

        JsonObject json = JsonParser.parseString(
                new com.google.gson.Gson().toJson(LifecycleListenerLedger.toReport(snapshot))).getAsJsonObject();
        assertEquals(1, json.get("schemaVersion").getAsInt());
        assertEquals(15, json.getAsJsonArray("slowest").size());
        assertEquals("mod15", json.getAsJsonArray("slowest").get(0).getAsJsonObject().get("mod").getAsString());
        assertEquals(1500.0, json.getAsJsonArray("slowest").get(0).getAsJsonObject()
                .get("durationMillis").getAsDouble(), 0.001);
    }

    @Test
    void reportSectionIsEmbeddedInTheStartupJson() {
        long origin = System.nanoTime() - 1_000_000;
        ServerStartupReport report = new ServerStartupReport(new ServerStartupReport.ClockMapping(
                1000, origin, origin + 100, "2026-10-24T00:00:00Z", 10, "test"), false);
        LifecycleListenerLedger ledger = new LifecycleListenerLedger(origin);
        Slot slot = slot(ledger, "ServerStartedEvent", "m", "c#x");
        ledger.record(slot, "t", origin + 10, origin + 20 * MS);
        report.lifecycleLedger(LifecycleListenerLedger.toReport(ledger.snapshot()));
        JsonObject json = JsonParser.parseString(report.toJson()).getAsJsonObject();
        assertEquals("m", json.getAsJsonObject("lifecycleListenerLedger")
                .getAsJsonArray("slowest").get(0).getAsJsonObject().get("mod").getAsString());
    }
}
