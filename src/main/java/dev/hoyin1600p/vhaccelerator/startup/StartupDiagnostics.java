package dev.hoyin1600p.vhaccelerator.startup;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;

/** Failure boundary for observational code only; never wrap game work in this guard. */
public final class StartupDiagnostics {
    private static final Set<Failure> unattachedFailures = new LinkedHashSet<>();
    private StartupDiagnostics() { }

    public static void run(String stage, Runnable action) {
        get(stage, () -> { action.run(); return null; }, null);
    }

    public static <T> T get(String stage, Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            failure(stage, failure);
            return fallback;
        }
    }

    public static void failure(String stage, Throwable failure) {
        failure(ServerLaunchTimer.currentReport(), stage, failure, detail ->
                LogManager.getLogger("VH Accelerator").warn(
                        "Startup diagnostics invalid at {}: {}: {}",
                        detail.stage(), detail.exceptionClass(), detail.reason()));
    }

    static void failure(ServerStartupReport report, String stage, Throwable failure,
                        Consumer<Failure> warning) {
        try {
            Failure detail = new Failure(bounded(stage, 96), failure.getClass().getName(),
                    bounded(failure.getMessage(), 384));
            boolean first;
            if (report != null) {
                first = report.diagnosticFailure(detail);
            } else {
                synchronized (unattachedFailures) {
                    first = unattachedFailures.size() < 64 && unattachedFailures.add(detail);
                }
            }
            if (first) {
                try {
                    warning.accept(detail);
                } catch (RuntimeException | LinkageError | AssertionError ignored) {
                    // A broken logging backend must not turn a diagnostic into a game failure.
                }
            }
        } catch (RuntimeException | LinkageError | AssertionError ignored) {
            // Failure recording itself is observational too.
        }
    }

    static void attach(ServerStartupReport report) {
        synchronized (unattachedFailures) {
            unattachedFailures.forEach(report::diagnosticFailure);
            unattachedFailures.clear();
        }
    }

    private static String bounded(String value, int limit) {
        if (value == null) return "unavailable";
        String safe = value.replaceAll("[\\p{Cntrl}]", " ");
        return safe.length() <= limit ? safe : safe.substring(0, limit);
    }

    /** Scalar, bounded metadata only; the original exception and its objects are never retained. */
    public record Failure(String stage, String exceptionClass, String reason) { }
}
