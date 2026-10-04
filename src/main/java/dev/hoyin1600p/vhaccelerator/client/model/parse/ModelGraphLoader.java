package dev.hoyin1600p.vhaccelerator.client.model.parse;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The single thread on which model graphs load after launch.
 *
 * <p>{@code ModelBakery#getModel} mutates the bakery's loading stack,
 * blockstate context and unbaked cache without synchronization. During the
 * initial load only one thread calls it, but once graphs are deferred to
 * first use, chunk-compile workers and the render thread could reach it at
 * the same time. Every post-launch cache miss therefore runs here, one at a
 * time. A graph load only reads resources and parses JSON; it never waits on
 * the render thread or a chunk worker, so callers waiting here cannot
 * deadlock with it.</p>
 */
public final class ModelGraphLoader {
    private static final ThreadLocal<Boolean> LOADER = ThreadLocal.withInitial(() -> false);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(() -> {
            LOADER.set(true);
            runnable.run();
        }, "VH Accelerator model graph loader");
        thread.setDaemon(true);
        return thread;
    });

    private ModelGraphLoader() {
    }

    public static boolean isLoaderThread() {
        return LOADER.get();
    }

    /**
     * Runs {@code load} on the loader thread and waits for it. Already on the
     * loader thread, it runs inline. Runtime exceptions and errors thrown by
     * {@code load} are rethrown to the caller.
     */
    public static <T> T call(Callable<T> load) {
        if (isLoaderThread()) {
            return run(load);
        }
        Future<T> future = EXECUTOR.submit(load);
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return future.get();
                } catch (InterruptedException interruption) {
                    // The load must finish either way; keep waiting and restore the flag.
                    interrupted = true;
                } catch (ExecutionException failure) {
                    Throwable cause = failure.getCause();
                    if (cause instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    if (cause instanceof Error error) {
                        throw error;
                    }
                    throw new IllegalStateException(cause);
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static <T> T run(Callable<T> load) {
        try {
            return load.call();
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception checked) {
            throw new IllegalStateException(checked);
        }
    }
}
