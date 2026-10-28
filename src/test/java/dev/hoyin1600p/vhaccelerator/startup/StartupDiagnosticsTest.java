package dev.hoyin1600p.vhaccelerator.startup;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StartupDiagnosticsTest {
    private Field activeReport;
    private ServerStartupReport previous;
    private ServerStartupReport report;
    private Field publishedGroups;
    private Object previousGroups;
    private Set<StartupDiagnostics.Failure> pendingFailures;
    private Set<StartupDiagnostics.Failure> previousPending;

    @BeforeEach
    void attachReport() throws Exception {
        report = report(false);
        activeReport = ServerLaunchTimer.class.getDeclaredField("report");
        activeReport.setAccessible(true);
        previous = (ServerStartupReport) activeReport.get(null);
        activeReport.set(null, report);
        publishedGroups = dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups.class.getDeclaredField("published");
        publishedGroups.setAccessible(true);
        previousGroups = publishedGroups.get(null);
        publishedGroups.set(null, null);
        Field pending = StartupDiagnostics.class.getDeclaredField("unattachedFailures");
        pending.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<StartupDiagnostics.Failure> failures = (Set<StartupDiagnostics.Failure>) pending.get(null);
        pendingFailures = failures;
        synchronized (pendingFailures) {
            previousPending = new java.util.LinkedHashSet<>(pendingFailures);
            pendingFailures.clear();
        }
    }

    @AfterEach
    void restoreReport() throws Exception {
        activeReport.set(null, previous);
        publishedGroups.set(null, previousGroups);
        synchronized (pendingFailures) {
            pendingFailures.clear();
            pendingFailures.addAll(previousPending);
        }
    }

    private static ServerStartupReport report(boolean detailed) {
        long now = System.nanoTime();
        Instant wall = Instant.now();
        ServerStartupReport report = new ServerStartupReport(new ServerStartupReport.ClockMapping(
                1000, now, now, wall.toString(), 0, "test"), detailed);
        report.checkpointAt("attachment", "test", now, wall);
        report.checkpointAt("done", "test", now + 1, wall);
        report.checkpointAt("server_started", "test", now + 2, wall);
        return report;
    }

    @Test
    void verificationFailureWarnsOnceAndInvalidatesOtherwiseCompleteReport() {
        assertEquals("complete", json(report.toJson()).get("completionState").getAsString());
        AtomicInteger warnings = new AtomicInteger();
        var logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger("VH Accelerator");
        AbstractAppender appender = new AbstractAppender("diagnostics-test", null, null, false, Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) {
                if (event.getLevel() == Level.WARN && event.getMessage().getFormattedMessage()
                        .contains("invalid fixture ordering")) warnings.incrementAndGet();
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            for (int i = 0; i < 2; i++) {
                assertDoesNotThrow(() -> StartupDiagnostics.run("compatibility.verify", () -> {
                    throw new IllegalStateException("invalid fixture ordering");
                }));
            }
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
        assertEquals(1, warnings.get());
        JsonObject json = json(report.toJson());
        assertEquals("incomplete", json.get("completionState").getAsString());
        assertEquals("startup_diagnostics_failed", json.get("errorState").getAsString());
        assertEquals(1, json.getAsJsonArray("diagnosticFailures").size());
        assertTrue(json.getAsJsonArray("diagnosticFailures").toString().contains("invalid fixture ordering"));
    }

    @Test
    void diagnosticLinkageAndAssertionFailuresAreContainedAndLoggingFailureIsContained() {
        assertDoesNotThrow(() -> StartupDiagnostics.run("adapter.linkage", () -> { throw new NoClassDefFoundError("fixture"); }));
        assertDoesNotThrow(() -> StartupDiagnostics.run("adapter.assertion", () -> { throw new AssertionError("fixture"); }));
        assertDoesNotThrow(() -> StartupDiagnostics.failure(report, "warn", new IllegalStateException("fixture"),
                failure -> { throw new AssertionError("broken backend"); }));
        assertEquals(3, json(report.toJson()).getAsJsonArray("diagnosticFailures").size());
    }

    @Test
    void invalidObservationMetadataCannotEscapeMixinCallback() throws Exception {
        report = report(true);
        activeReport.set(null, report);
        Map<String, Object> broken = new BrokenMetadata();
        assertDoesNotThrow(() -> ServerLaunchTimer.observation("fixture", broken));
        JsonObject json = json(report.toJson());
        assertEquals("incomplete", json.get("completionState").getAsString());
        assertEquals("startup_diagnostics_failed", json.get("errorState").getAsString());
        assertTrue(json.getAsJsonArray("diagnosticFailures").toString().contains("invalid observer metadata"));
    }

    @Test
    void serializationFailureWritesMinimalInvalidReport() {
        AtomicReference<String> written = new AtomicReference<>();
        AtomicReference<String> logged = new AtomicReference<>();
        assertDoesNotThrow(() -> ServerLaunchTimer.writeReport(report,
                () -> { throw new AssertionError("serializer fixture"); }, () -> Path.of("fixture.json"),
                (path, text) -> written.set(text), logged::set));
        assertEquals(written.get(), logged.get());
        assertInvalidFallback(logged.get(), "report.serialize");
    }

    @Test
    void pathFailureAndWriterFailureProduceInvalidStructuredFallback() {
        AtomicReference<String> logged = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        assertDoesNotThrow(() -> ServerLaunchTimer.writeReport(report, report::toJson,
                () -> { throw new IllegalStateException("path fixture"); },
                (path, text) -> writes.incrementAndGet(), logged::set));
        assertEquals(0, writes.get());
        assertInvalidFallback(logged.get(), "report.write");
        assertDoesNotThrow(() -> ServerLaunchTimer.writeReport(report, report::toJson, () -> Path.of("fixture.json"),
                (path, text) -> { throw new IOException("writer fixture"); }, logged::set));
        assertInvalidFallback(logged.get(), "writer fixture");
    }

    @Test
    void preAttachmentFailuresAreCarriedIntoReportAndFailureMetadataIsBounded() {
        AtomicInteger warnings = new AtomicInteger();
        StartupDiagnostics.failure(null, "before.attachment", new IllegalStateException("x".repeat(1000)),
                failure -> warnings.incrementAndGet());
        StartupDiagnostics.attach(report);
        JsonObject json = json(report.toJson());
        assertEquals("startup_diagnostics_failed", json.get("errorState").getAsString());
        JsonObject pending = java.util.stream.StreamSupport.stream(json.getAsJsonArray("diagnosticFailures").spliterator(), false)
                .map(value -> value.getAsJsonObject()).filter(value -> value.get("stage").getAsString().equals("before.attachment"))
                .findFirst().orElseThrow();
        assertEquals(384, pending.get("reason").getAsString().length());
        assertEquals(1, warnings.get());
    }

    @Test
    void structuredLoggingFailureIsPersistedInSurvivingFile() {
        AtomicReference<String> written = new AtomicReference<>();
        assertDoesNotThrow(() -> ServerLaunchTimer.writeReport(report, report::toJson,
                () -> Path.of("fixture.json"), (path, text) -> written.set(text),
                text -> { throw new IllegalStateException("log fixture"); }));
        assertInvalidFallback(written.get(), "report.log");
    }

    private static final class BrokenMetadata extends AbstractMap<String, Object> {
        @Override public Set<Entry<String, Object>> entrySet() {
            throw new IllegalArgumentException("invalid observer metadata");
        }
    }

    @Test
    void failedInitialAttachmentStillFinalizesInvalidReportBeforeReleasingWindow() throws Exception {
        Field attempted = ServerLaunchTimer.class.getDeclaredField("ATTACHMENT_ATTEMPTED");
        Field end = ServerLaunchTimer.class.getDeclaredField("END_NANOS");
        attempted.setAccessible(true);
        end.setAccessible(true);
        var attemptedFlag = (java.util.concurrent.atomic.AtomicBoolean) attempted.get(null);
        var endValue = (java.util.concurrent.atomic.AtomicLong) end.get(null);
        boolean oldAttempted = attemptedFlag.getAndSet(false);
        long oldEnd = endValue.getAndSet(-1);
        activeReport.set(null, null);
        AtomicReference<String> written = new AtomicReference<>();
        try {
            ServerLaunchTimer.markStart(() -> { throw new AssertionError("attachment fixture"); });
            assertDoesNotThrow(() -> ServerLaunchTimer.markEnd(() -> false, () -> fail("timers off"),
                    completed -> {
                        assertSame(completed, ServerLaunchTimer.currentReport());
                        written.set(completed.toJson());
                    }));
            assertInvalidFallback(written.get(), "attachment fixture");
            assertNull(ServerLaunchTimer.currentReport());
        } finally {
            attemptedFlag.set(oldAttempted);
            endValue.set(oldEnd);
        }
    }

    @Test
    void verificationFailureIsInFinalJsonBeforeOutputAndWindowCloses() throws Exception {
        Field attempted = ServerLaunchTimer.class.getDeclaredField("ATTACHMENT_ATTEMPTED");
        Field end = ServerLaunchTimer.class.getDeclaredField("END_NANOS");
        attempted.setAccessible(true);
        end.setAccessible(true);
        var attemptedFlag = (java.util.concurrent.atomic.AtomicBoolean) attempted.get(null);
        var endValue = (java.util.concurrent.atomic.AtomicLong) end.get(null);
        boolean oldAttempted = attemptedFlag.getAndSet(true);
        long oldEnd = endValue.getAndSet(-1);
        AtomicReference<String> written = new AtomicReference<>();
        try {
            assertDoesNotThrow(() -> ServerLaunchTimer.markEnd(() -> true,
                    () -> { throw new IllegalStateException("final verification fixture"); },
                    completed -> written.set(completed.toJson())));
            assertInvalidFallback(written.get(), "final verification fixture");
            assertNull(ServerLaunchTimer.currentReport());
        } finally {
            attemptedFlag.set(oldAttempted);
            endValue.set(oldEnd);
        }
    }

    @Test
    void diagnosticCompletionCannotFailSuccessfulReloadOrReplaceRealDelegateFailure() {
        Runnable diagnostic = () -> { throw new AssertionError("completion fixture"); };
        var successful = ReloadListenerTimer.wrap(java.util.List.of(listener(
                () -> java.util.concurrent.CompletableFuture.completedFuture(null))), "fixture", diagnostic).get(0);
        assertDoesNotThrow(() -> successful.reload(null, null, null, null, Runnable::run, Runnable::run).join());
        var original = new IllegalStateException("real delegate failure");
        var failing = ReloadListenerTimer.wrap(java.util.List.of(listener(() -> { throw original; })),
                "fixture", diagnostic).get(0);
        assertSame(original, assertThrows(IllegalStateException.class,
                () -> failing.reload(null, null, null, null, Runnable::run, Runnable::run)));
        var async = ReloadListenerTimer.wrap(java.util.List.of(listener(
                () -> java.util.concurrent.CompletableFuture.failedFuture(original))), "fixture", diagnostic).get(0);
        assertSame(original, assertThrows(java.util.concurrent.CompletionException.class,
                () -> async.reload(null, null, null, null, Runnable::run, Runnable::run).join()).getCause());
    }

    @Test
    void observedExecutorPreservesRealCommandAndExecutorFailures() throws Exception {
        var observe = ServerReloadObservation.class.getDeclaredMethod("observeExecutor",
                java.util.concurrent.Executor.class, String.class, String.class, long.class, String.class);
        observe.setAccessible(true);
        var original = new IllegalStateException("real command failure");
        java.util.concurrent.Executor direct = Runnable::run;
        var observed = (java.util.concurrent.Executor) observe.invoke(null, direct, "fixture", null, 1L, "fixture");
        assertSame(original, assertThrows(IllegalStateException.class, () -> observed.execute(() -> { throw original; })));
        var rejected = new java.util.concurrent.RejectedExecutionException("real rejection");
        java.util.concurrent.Executor rejecting = command -> { throw rejected; };
        var observedReject = (java.util.concurrent.Executor) observe.invoke(null, rejecting, "fixture", null, 1L, "fixture");
        assertSame(rejected, assertThrows(java.util.concurrent.RejectedExecutionException.class,
                () -> observedReject.execute(() -> fail("rejected command ran"))));
    }

    @Test
    void failureRecordingFromGroupCallbackDoesNotWaitForReportMonitor() throws Exception {
        var groups = new dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups(
                java.util.List.of(), (target, mixin) -> true, name -> null);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var finished = new java.util.concurrent.CountDownLatch(1);
        Thread callback = new Thread(() -> {
            synchronized (groups) {
                entered.countDown();
                StartupDiagnostics.failure("compatibility.callback", new IllegalStateException("concurrent fixture"));
                finished.countDown();
            }
        }, "diagnostic-callback");
        boolean completedWhileSerializing;
        // Serialization owns this monitor before taking a compatibility snapshot. A callback
        // holding the group monitor must be able to record its failure without waiting for it.
        synchronized (report) {
            callback.start();
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            completedWhileSerializing = finished.await(2, java.util.concurrent.TimeUnit.SECONDS);
        }
        callback.join(2000); // The old implementation can finish after the monitor is released.
        assertFalse(callback.isAlive());
        assertTrue(completedWhileSerializing, "failure recorder waited for the report monitor");
        assertInvalidFallback(report.toJson(), "concurrent fixture");
    }

    private static net.minecraft.server.packs.resources.PreparableReloadListener listener(
            java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> action) {
        return new net.minecraft.server.packs.resources.PreparableReloadListener() {
            @Override public java.util.concurrent.CompletableFuture<Void> reload(PreparationBarrier barrier,
                    net.minecraft.server.packs.resources.ResourceManager resources,
                    net.minecraft.util.profiling.ProfilerFiller prepare, net.minecraft.util.profiling.ProfilerFiller apply,
                    java.util.concurrent.Executor background, java.util.concurrent.Executor game) {
                return action.get();
            }
        };
    }

    private static void assertInvalidFallback(String text, String reason) {
        JsonObject json = json(text);
        assertEquals("incomplete", json.get("completionState").getAsString());
        assertFalse(json.get("errorState").isJsonNull());
        assertTrue(json.getAsJsonArray("diagnosticFailures").toString().contains(reason));
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
