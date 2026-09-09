package dev.hoyin1600p.vhaccelerator.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded blocking-I/O lane; CPU pools must not wait for optional network calls. */
public final class NetworkWorkers implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    public NetworkWorkers(int processors) {
        int size = Math.max(1, Math.min(2, processors));
        var sequence = new AtomicInteger();
        executor = new ThreadPoolExecutor(size, size, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), task -> {
                    var thread = new Thread(task, "VH Accelerator network-" + sequence.incrementAndGet());
                    thread.setContextClassLoader(NetworkWorkers.class.getClassLoader());
                    thread.setDaemon(true);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                });
        executor.allowCoreThreadTimeOut(true);
    }
    public Executor executor() { return executor; }
    private static final class Holder {
        private static final NetworkWorkers WORKERS = new NetworkWorkers(Runtime.getRuntime().availableProcessors());
    }
    public static Executor shared() { return Holder.WORKERS.executor(); }
    @Override public void close() { executor.shutdownNow(); }
}
