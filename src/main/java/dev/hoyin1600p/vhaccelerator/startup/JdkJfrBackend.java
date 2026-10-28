package dev.hoyin1600p.vhaccelerator.startup;

import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;

/**
 * The only class that links {@code jdk.jfr}. {@link #available()} is checked first, and the
 * recorder catches linkage errors, so a JVM without the module costs nothing.
 */
final class JdkJfrBackend implements StartupJfrRecorder.Backend {
    private static final long MAX_BYTES = 256L * 1024 * 1024;
    private static final Duration MAX_AGE = Duration.ofMinutes(30);

    @Override
    public boolean available() {
        try {
            return ModuleLayer.boot().findModule("jdk.jfr").isPresent() && FlightRecorder.isAvailable();
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    @Override
    public StartupJfrRecorder.Handle start(String name, Path destination) throws Exception {
        Recording recording = new Recording(Configuration.getConfiguration("profile"));
        try {
            recording.setName(name);
            recording.setToDisk(true);
            recording.setMaxSize(MAX_BYTES);
            recording.setMaxAge(MAX_AGE);
            recording.setDestination(destination);
            recording.setDumpOnExit(true);
            // Low thresholds so a short stall inside one listener or one wait is still visible.
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
            for (String event : new String[] {"jdk.ThreadPark", "jdk.ThreadSleep", "jdk.JavaMonitorWait",
                    "jdk.JavaMonitorEnter", "jdk.SocketRead", "jdk.SocketWrite", "jdk.FileRead", "jdk.FileWrite"}) {
                recording.enable(event).withThreshold(Duration.ofMillis(5)).withStackTrace();
            }
            recording.start();
        } catch (Exception | LinkageError failure) {
            recording.close();
            throw failure;
        }
        return () -> {
            try {
                recording.stop();
            } finally {
                recording.close();
            }
        };
    }
}
