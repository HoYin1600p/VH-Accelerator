package dev.hoyin1600p.vhaccelerator.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Shared, lazily created workers. One budget covers compute, coordinators and
 * disk/network work together. I/O has a separate serial lane so a compute
 * worker waiting for a cache cannot starve its reader. Fork/join compensation
 * is explicitly capped; nested batches use join-helping, not blocking futures.
 */
public final class SharedWorkers {
    private static final WorkerBudget BUDGET = WorkerBudget.forProcessors(
            Runtime.getRuntime().availableProcessors());

    private SharedWorkers() { }

    public static WorkerBudget budget() { return BUDGET; }

    private static final class Holder {
        private static final Lanes LANES = new Lanes(BUDGET);
    }

    public static Executor compute() { return Holder.LANES.computeExecutor(); }
    public static Executor io() { return Holder.LANES.ioExecutor(); }
    /** Network waits should not hold up disk-cache readers on larger CPUs. */
    public static Executor background() { return BUDGET.compute() == 0 ? io() : compute(); }
    public static <T> T invoke(Supplier<T> action) { return Holder.LANES.invoke(action); }
    public static <T> void forEach(List<T> values, Consumer<T> action) {
        Holder.LANES.forEach(values, action);
    }
    public static void forRange(int size, IntConsumer action) {
        Holder.LANES.forRange(size, action);
    }

    /** Never let a sequential fallback escape into the JVM common pool. */
    public static <T> Stream<T> stream(Collection<T> values) {
        return Holder.LANES.isComputeWorker() ? values.parallelStream() : values.stream();
    }

    static final class Lanes implements AutoCloseable {
        private final ForkJoinPool pool;
        private final ExecutorService ioPool;
        private final ThreadLocal<Boolean> inIo = ThreadLocal.withInitial(() -> false);

        Lanes(WorkerBudget budget) {
            ClassLoader loader = SharedWorkers.class.getClassLoader();
            pool = budget.compute() == 0 ? null : new ForkJoinPool(
                    budget.compute(), owner -> {
                        var thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(owner);
                        thread.setName("VH Accelerator compute-" + thread.getPoolIndex());
                        thread.setContextClassLoader(loader);
                        thread.setPriority(Thread.MIN_PRIORITY);
                        thread.setDaemon(true);
                        return thread;
                    }, null, true, budget.compute(), budget.compute(), 1,
                    ignored -> true, 60L, TimeUnit.SECONDS);
            ioPool = budget.io() == 0 ? null : Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "VH Accelerator shared I/O");
                thread.setContextClassLoader(loader);
                thread.setPriority(Thread.MIN_PRIORITY);
                thread.setDaemon(true);
                return thread;
            });
        }

        boolean isComputeWorker() { return pool != null && ForkJoinTask.getPool() == pool; }

        Executor computeExecutor() {
            return task -> {
                if (isComputeWorker()) {
                    task.run();
                } else if (pool != null) {
                    pool.execute(task);
                } else {
                    // Do not queue a coordinator behind the reader it may
                    // await. No additional compute workers on tiny CPUs.
                    task.run();
                }
            };
        }

        Executor ioExecutor() {
            return task -> {
                if (ioPool == null || inIo.get()) {
                    task.run();
                } else {
                    ioPool.execute(() -> {
                        inIo.set(true);
                        try { task.run(); } finally { inIo.remove(); }
                    });
                }
            };
        }

        <T> T invoke(Supplier<T> action) {
            return pool == null || isComputeWorker() ? action.get() : pool.submit(action::get).join();
        }

        <T> void forEach(List<T> values, Consumer<T> action) {
            forRange(values.size(), index -> action.accept(values.get(index)));
        }

        void forRange(int count, IntConsumer action) {
            if (count <= 0) { return; }
            if (pool == null) {
                for (int i = 0; i < count; i++) { action.accept(i); }
                return;
            }
            int workers = Math.min(pool.getParallelism(), count);
            int size = (count + workers - 1) / workers;
            List<ForkJoinTask<?>> tasks = new ArrayList<>(workers);
            for (int start = 0; start < count; start += size) {
                int from = start;
                int to = Math.min(start + size, count);
                tasks.add(pool.submit(() -> {
                    for (int i = from; i < to; i++) { action.accept(i); }
                }));
            }
            Throwable failure = null;
            // Quiesce ALL tasks before a caller takes its sequential fallback.
            for (ForkJoinTask<?> task : tasks) {
                try { task.join(); } catch (RuntimeException | Error thrown) {
                    if (failure == null) { failure = thrown; }
                }
            }
            if (failure instanceof RuntimeException exception) { throw exception; }
            if (failure instanceof Error error) { throw error; }
        }

        @Override public void close() {
            if (pool != null) { pool.shutdownNow(); }
            if (ioPool != null) { ioPool.shutdownNow(); }
        }
    }
}
