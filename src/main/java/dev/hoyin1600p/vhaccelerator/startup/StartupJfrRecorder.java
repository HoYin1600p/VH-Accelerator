package dev.hoyin1600p.vhaccelerator.startup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Starts one bounded JDK Flight Recorder recording for a server launch and writes it to
 * {@code startup-<runId>.jfr}. Every failure, including a JVM without JFR, leaves the launch
 * unchanged: the recorder then reports itself unavailable and does nothing further.
 */
public final class StartupJfrRecorder {
    /** Seconds the recording keeps running after the started event has finished. */
    public static final long TAIL_SECONDS = 30L;

    /** Access to the JFR API, replaceable so unavailability and failures can be tested. */
    interface Backend {
        boolean available();

        /** Starts recording to {@code destination}; the file is written when the handle stops. */
        Handle start(String name, Path destination) throws Exception;
    }

    interface Handle {
        void stopAndWrite() throws Exception;
    }

    public enum State { IDLE, RECORDING, STOPPED, UNAVAILABLE, FAILED }

    private final Backend backend;
    private final Consumer<String> info;
    private final Consumer<String> warn;
    private Handle handle;
    private Path destination;
    private State state = State.IDLE;

    public StartupJfrRecorder(Consumer<String> info, Consumer<String> warn) {
        this(loadBackend(), info, warn);
    }

    StartupJfrRecorder(Backend backend, Consumer<String> info, Consumer<String> warn) {
        this.backend = backend;
        this.info = info;
        this.warn = warn;
    }

    private static Backend loadBackend() {
        try {
            return new JdkJfrBackend();
        } catch (RuntimeException | LinkageError missing) {
            return new Backend() {
                @Override public boolean available() { return false; }
                @Override public Handle start(String name, Path destination) {
                    throw new IllegalStateException("JFR unavailable");
                }
            };
        }
    }

    public synchronized State state() {
        return state;
    }

    public synchronized Path destination() {
        return destination;
    }

    /** @return true when a recording is running. Never throws. */
    public synchronized boolean start(Path logDirectory, String runId) {
        if (state != State.IDLE) return state == State.RECORDING;
        try {
            if (!backend.available()) {
                state = State.UNAVAILABLE;
                warn.accept("Startup JFR recording requested, but Java Flight Recorder is not available on this JVM");
                return false;
            }
            Files.createDirectories(logDirectory);
            Path target = logDirectory.resolve("startup-" + runId + ".jfr");
            prune(logDirectory, target);
            handle = backend.start("VHA startup " + runId, target);
            destination = target;
            state = State.RECORDING;
            info.accept("Startup JFR recording started; it will be written to " + target);
            return true;
        } catch (IOException | RuntimeException | LinkageError | AssertionError failure) {
            fail("start", failure);
        } catch (Exception failure) {
            fail("start", failure);
        }
        return false;
    }

    /** Recordings kept in the log directory, including the one being started. */
    public static final int KEEP_RECORDINGS = 3;

    /** Deletes older startup recordings so that at most {@link #KEEP_RECORDINGS} remain with the new one. */
    static void prune(Path logDirectory, Path current) {
        try (java.util.stream.Stream<Path> files = Files.list(logDirectory)) {
            List<Path> old = files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith("startup-") && name.endsWith(".jfr") && !path.equals(current);
            }).sorted(java.util.Comparator.comparingLong(StartupJfrRecorder::modified).reversed()
                    .thenComparing(java.util.Comparator.reverseOrder())).toList();
            for (int i = KEEP_RECORDINGS - 1; i < old.size(); i++) {
                try {
                    Files.deleteIfExists(old.get(i));
                } catch (IOException | RuntimeException ignored) {
                    // Pruning is housekeeping only.
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Nothing to prune or unreadable directory.
        }
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException failure) {
            return 0L;
        }
    }

    /** Stops the recording and writes the file. Safe to call repeatedly or without a start. */
    public synchronized Path stop() {
        if (state != State.RECORDING) return null;
        Handle active = handle;
        handle = null;
        try {
            active.stopAndWrite();
            state = State.STOPPED;
            info.accept("Startup JFR recording written to " + destination);
            return destination;
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            fail("stop", failure);
        } catch (Exception failure) {
            fail("stop", failure);
        }
        return null;
    }

    /** Stops the recording after the given delay on a daemon thread. */
    public Thread stopLater(long delayMillis) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(Math.max(0L, delayMillis));
            } catch (InterruptedException interrupted) {
                // Stop immediately so the data is still written.
            }
            stop();
        }, "VHA startup JFR stop");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void fail(String stage, Throwable failure) {
        state = State.FAILED;
        try {
            warn.accept("Startup JFR recording could not " + stage + ": "
                    + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        } catch (RuntimeException | LinkageError | AssertionError ignored) {
            // Logging is observational too.
        }
    }
}
