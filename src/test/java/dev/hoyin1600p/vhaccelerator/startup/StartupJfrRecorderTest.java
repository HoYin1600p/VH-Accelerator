package dev.hoyin1600p.vhaccelerator.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupJfrRecorderTest {
    @TempDir Path temp;

    @Test
    void realRecordingStartsStopsAndWritesTheNamedFile() throws Exception {
        List<String> info = new ArrayList<>();
        List<String> warn = new ArrayList<>();
        StartupJfrRecorder recorder = new StartupJfrRecorder(info::add, warn::add);
        Assumptions.assumeTrue(recorder.start(temp.resolve("logs/vhaccelerator"), "run-1"),
                "JFR is not available on this JVM: " + warn);
        assertEquals(StartupJfrRecorder.State.RECORDING, recorder.state());
        Thread.sleep(50);
        Path written = recorder.stop();
        assertEquals(temp.resolve("logs/vhaccelerator/startup-run-1.jfr"), written);
        assertTrue(Files.size(written) > 0);
        assertEquals(StartupJfrRecorder.State.STOPPED, recorder.state());
        assertNull(recorder.stop(), "a second stop is a no-op");
        assertTrue(info.stream().anyMatch(line -> line.contains("startup-run-1.jfr")));
        assertTrue(warn.isEmpty());
    }

    @Test
    void stopLaterWritesTheFileOnADaemonThread() throws Exception {
        StartupJfrRecorder recorder = new StartupJfrRecorder(line -> { }, line -> { });
        Assumptions.assumeTrue(recorder.start(temp, "run-2"));
        Thread thread = recorder.stopLater(20);
        assertTrue(thread.isDaemon());
        thread.join(30_000);
        assertEquals(StartupJfrRecorder.State.STOPPED, recorder.state());
        assertTrue(Files.size(temp.resolve("startup-run-2.jfr")) > 0);
    }

    @Test
    void oldRecordingsBeyondTheNewestThreeAreDeleted() throws Exception {
        for (int i = 1; i <= 5; i++) {
            Path old = temp.resolve("startup-old" + i + ".jfr");
            Files.writeString(old, "x");
            Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(1_000_000L * i));
        }
        Files.writeString(temp.resolve("other.jfr"), "keep");
        Files.writeString(temp.resolve("startup-report.json"), "keep");
        StartupJfrRecorder recorder = new StartupJfrRecorder(new FakeBackend(true, null), line -> { }, line -> { });
        assertTrue(recorder.start(temp, "new"));
        List<String> left;
        try (var files = Files.list(temp)) {
            left = files.map(path -> path.getFileName().toString()).sorted().toList();
        }
        // The new recording is not on disk yet; the two newest old ones remain beside it once written.
        assertEquals(List.of("other.jfr", "startup-old4.jfr", "startup-old5.jfr", "startup-report.json"), left);
    }

    @Test
    void unavailableJfrFailsSoftWithOneWarningAndNoFile() {
        List<String> warn = new ArrayList<>();
        StartupJfrRecorder recorder = new StartupJfrRecorder(new FakeBackend(false, null), line -> { }, warn::add);
        assertFalse(recorder.start(temp, "run-3"));
        assertEquals(StartupJfrRecorder.State.UNAVAILABLE, recorder.state());
        assertEquals(1, warn.size());
        assertFalse(recorder.start(temp, "run-3"), "does not retry");
        assertEquals(1, warn.size());
        assertNull(recorder.stop());
        assertFalse(Files.exists(temp.resolve("startup-run-3.jfr")));
    }

    @Test
    void backendFailuresAtStartAndStopFailSoft() {
        List<String> warn = new ArrayList<>();
        StartupJfrRecorder startFails = new StartupJfrRecorder(
                new FakeBackend(true, new NoClassDefFoundError("jdk/jfr/Recording")), line -> { }, warn::add);
        assertFalse(startFails.start(temp, "run-4"));
        assertEquals(StartupJfrRecorder.State.FAILED, startFails.state());

        StartupJfrRecorder stopFails = new StartupJfrRecorder(new Backend2(), line -> { }, warn::add);
        assertTrue(stopFails.start(temp, "run-5"));
        assertNull(stopFails.stop());
        assertEquals(StartupJfrRecorder.State.FAILED, stopFails.state());
        assertEquals(2, warn.size());
    }

    private record FakeBackend(boolean available, Throwable startFailure) implements StartupJfrRecorder.Backend {
        @Override
        public StartupJfrRecorder.Handle start(String name, Path destination) throws Exception {
            if (startFailure instanceof Error error) throw error;
            if (startFailure instanceof Exception exception) throw exception;
            return () -> { };
        }
    }

    private static final class Backend2 implements StartupJfrRecorder.Backend {
        @Override public boolean available() { return true; }

        @Override
        public StartupJfrRecorder.Handle start(String name, Path destination) {
            return () -> { throw new IOException("disk full"); };
        }
    }
}
