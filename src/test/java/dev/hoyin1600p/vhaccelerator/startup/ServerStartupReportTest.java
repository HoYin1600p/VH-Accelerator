package dev.hoyin1600p.vhaccelerator.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ServerStartupReportTest {
    private static final long ORIGIN = System.nanoTime() - 1_000_000;
    private static final Instant WALL = Instant.parse("2026-10-23T18:00:00Z");

    private ServerStartupReport report(boolean detailed, int limit) {
        return new ServerStartupReport(new ServerStartupReport.ClockMapping(
                1000, ORIGIN, ORIGIN + 100, WALL.toString(), 10, "test mapping"), detailed, limit);
    }

    @Test
    void checkpointsAreOnceOnlyOrderedAndMissingEventsAreExplicit() {
        ServerStartupReport report = report(true, 20);
        report.checkpointAt("attachment", "hook", ORIGIN + 100, WALL);
        report.checkpointAt("done", "hook", ORIGIN + 200, WALL.plusMillis(1));
        report.checkpointAt("done", "duplicate", ORIGIN + 250, WALL.plusMillis(2));
        report.checkpointAt("server_started", "hook", ORIGIN + 300, WALL.plusMillis(3));
        JsonObject json = JsonParser.parseString(report.toJson()).getAsJsonObject();
        assertEquals(1, json.get("schemaVersion").getAsInt());
        assertEquals("DEDICATED_SERVER", json.get("physicalSide").getAsString());
        assertEquals("incomplete", json.get("completionState").getAsString());
        assertTrue(json.get("missingCheckpoints").toString().contains("registry_start"));
        assertFalse(json.get("missingCheckpoints").toString().contains("done"));
        JsonArray records = json.getAsJsonArray("records");
        assertEquals(3, records.size());
        for (int i = 0; i < records.size(); i++) {
            JsonObject record = records.get(i).getAsJsonObject();
            assertEquals(json.get("runId"), record.get("runId"));
            JsonObject event = record.getAsJsonObject("event");
            assertEquals((i + 1) * 100, event.get("elapsedNanos").getAsLong());
            assertTrue(event.get("wallClock").getAsString().endsWith("Z"));
            assertTrue(event.get("threadId").getAsLong() > 0);
            JsonObject memory = event.getAsJsonObject("memory");
            assertTrue(memory.has("heapUsedBytes"));
            assertTrue(memory.has("heapCommittedBytes"));
            assertTrue(memory.has("heapMaxBytes"));
            assertTrue(memory.has("metaspaceUsedBytes"));
            assertTrue(memory.has("metaspaceCommittedBytes"));
            assertTrue(memory.has("metaspaceMaxBytes"));
            assertTrue(memory.has("collectionCount"));
            assertTrue(memory.has("collectionTimeMillis"));
        }
    }

    @Test
    void spansRetainExplicitAsyncParentsAndFailuresWithoutRetainingThrowables() {
        ServerStartupReport report = report(true, 20);
        var parent = report.beginSpan("reload", null, 1L, null);
        var child = report.beginSpan("recipes", parent.id(), 1L, "crafttweaker");
        report.endSpan(child, new IllegalStateException("not retained"));
        report.endSpan(child, null);
        report.endSpan(parent, null);
        JsonObject json = JsonParser.parseString(report.toJson()).getAsJsonObject();
        assertEquals(0, json.getAsJsonArray("unfinishedSpans").size());
        JsonArray records = json.getAsJsonArray("records");
        assertEquals(4, records.size());
        JsonObject end = records.get(2).getAsJsonObject().getAsJsonObject("event");
        assertEquals(parent.id(), end.get("parentId").getAsString());
        assertEquals(1, end.get("reloadGeneration").getAsLong());
        assertEquals("crafttweaker", end.get("featureGroup").getAsString());
        assertEquals("failed", end.get("completionState").getAsString());
        assertEquals(IllegalStateException.class.getName(), end.get("errorState").getAsString());
        assertTrue(end.get("endElapsedNanos").getAsLong()
                >= end.get("startElapsedNanos").getAsLong());
        assertFalse(report.toJson().contains("not retained"));
    }

    @Test
    void disabledDetailAndBufferOverflowAreObservable() {
        ServerStartupReport disabled = report(false, 1);
        assertEquals(null, disabled.beginSpan("unused", null, null, null));
        assertEquals(0, JsonParser.parseString(disabled.toJson()).getAsJsonObject()
                .getAsJsonArray("records").size());
        ServerStartupReport bounded = report(true, 1);
        var span = bounded.beginSpan("incomplete", null, null, null);
        bounded.endSpan(span, null);
        JsonObject json = JsonParser.parseString(bounded.toJson()).getAsJsonObject();
        assertEquals(1, json.getAsJsonArray("records").size());
        assertEquals(1, json.get("droppedRecords").getAsInt());
        assertEquals("record_limit_exceeded", json.get("errorState").getAsString());
        assertEquals(1, json.getAsJsonArray("unfinishedSpans").size());
    }

    @Test
    void nestedWallWorkIsNotReportedAsCriticalPathAndCrossThreadCpuIsUnavailable() throws Exception {
        ServerStartupReport report = report(true, 20);
        var parent = report.beginSpan("parent", null, null, null);
        var child = report.beginSpan("reload.prepare.task", parent.id(), 2L, "recipes");
        report.endSpan(child, null);
        Thread completion = new Thread(() -> report.endSpan(parent, null), "async-completion");
        completion.start();
        completion.join();
        JsonObject json = JsonParser.parseString(report.toJson()).getAsJsonObject();
        JsonObject totals = json.getAsJsonObject("timingSummary");
        assertTrue(totals.get("wallIntervalUnionNanos").getAsLong()
                <= totals.get("inclusiveSpanSumNanos").getAsLong());
        assertEquals(1, totals.getAsJsonObject("perThreadTasks").size());
        JsonObject parentEnd = json.getAsJsonArray("records").get(3).getAsJsonObject().getAsJsonObject("event");
        assertTrue(parentEnd.get("cpuNanos").isJsonNull());
    }

    @Test
    void observationMetadataIsSnapshottedRatherThanRetainingMutableInputs() {
        ServerStartupReport report = report(true, 20);
        var values = new java.util.HashMap<String, Object>();
        values.put("count", 4);
        report.observation("resources", values);
        values.put("count", 9);
        assertEquals(4, JsonParser.parseString(report.toJson()).getAsJsonObject()
                .getAsJsonObject("observations").getAsJsonObject("resources").get("count").getAsInt());
    }

    @Test
    void invalidEndpointOrderCannotProduceACompleteRoutineReport() {
        ServerStartupReport report = report(false, 20);
        report.checkpointAt("attachment", "hook", ORIGIN + 100, WALL);
        report.checkpointAt("done", "hook", ORIGIN + 300, WALL);
        report.checkpointAt("server_started", "hook", ORIGIN + 200, WALL);
        JsonObject json = JsonParser.parseString(report.toJson()).getAsJsonObject();
        assertEquals("invalid", json.getAsJsonObject("endpointOrder").get("state").getAsString());
        assertEquals("incomplete", json.get("completionState").getAsString());
        assertEquals("invalid_endpoint_order", json.get("errorState").getAsString());
    }
}
