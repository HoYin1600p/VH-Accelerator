package dev.hoyin1600p.vhaccelerator.compat.smoothboot;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import dev.hoyin1600p.vhaccelerator.bootstrap.ConfigMigration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Restores normal priority for the worker threads that Smooth Boot (Reloaded)
 * 0.0.4 lowers to {@link Thread#MIN_PRIORITY}.
 *
 * <p>Smooth Boot replaces Minecraft's bootstrap, background ("Main") and IO
 * executors at the head of the {@code Util} getters and overwrites Forge's
 * {@code ModWorkManager.newForkJoinWorkerThread}; every thread those create
 * receives the priority from {@code config/smoothboot.json}, which defaults to
 * 1 (Windows {@code THREAD_PRIORITY_LOWEST}). VH Accelerator runs its parallel
 * model, texture and JEI work on those pools, and a dedicated server loads its
 * chunks on them, so under any CPU contention that work loses to every
 * normal-priority process on the machine.
 *
 * <p>Rather than replacing Smooth Boot's executors, this helper wraps the
 * thread factories it installed, so threads created later (cached IO threads
 * recycle every minute) also start at {@link Thread#NORM_PRIORITY}. Smooth
 * Boot's thread counts, names, logging, async mode and exception handlers are
 * kept. A {@link ThreadPoolExecutor} accepts a new factory in place; a
 * {@link ForkJoinPool} cannot, so it is exchanged for an identically configured
 * twin before any of its workers exist. Priorities are only ever raised, so a
 * pack that already configures Smooth Boot to 5 or higher is left alone.
 * Common-side; no Smooth Boot class dependencies.
 */
public final class SmoothBootThreadPriorities {
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");
    static final int TARGET_PRIORITY = Thread.NORM_PRIORITY;
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final Object TWIN_LOCK = new Object();
    private static final Map<ForkJoinPool, ForkJoinPool> TWINS = new IdentityHashMap<>();

    private SmoothBootThreadPriorities() {
    }

    public static boolean enabled() {
        return readEnabled(FMLPaths.CONFIGDIR.get().resolve(ConfigMigration.COMMON_CONFIG));
    }

    static boolean readEnabled(Path path) {
        if (!Files.isRegularFile(path)) {
            return true;
        }
        try (CommentedFileConfig config = CommentedFileConfig.of(path)) {
            config.load();
            Object value = config.get(List.of("compatibility", "restoreSmoothBootThreadPriorities"));
            return !(value instanceof Boolean enabled) || enabled;
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "Could not read the Smooth Boot thread-priority setting; retaining the default",
                    failure
            );
            return true;
        }
    }

    /**
     * Returns an executor whose future threads start at normal priority. The
     * same instance comes back when it is already covered or is of an unknown
     * type; a {@link ForkJoinPool} is replaced by a twin that the caller must
     * publish in place of the original.
     */
    public static ExecutorService restore(String name, ExecutorService executor) {
        if (executor instanceof ThreadPoolExecutor pool) {
            ThreadFactory factory = pool.getThreadFactory();
            if (!(factory instanceof RaisingThreadFactory)) {
                pool.setThreadFactory(new RaisingThreadFactory(name, factory));
                LOGGER.debug("Wrapped the {} thread factory to restore normal priority", name);
            }
            return executor;
        }
        if (executor instanceof ForkJoinPool pool) {
            if (pool.getFactory() instanceof RaisingForkJoinFactory) {
                return pool;
            }
            synchronized (TWIN_LOCK) {
                ForkJoinPool twin = TWINS.get(pool);
                if (twin == null) {
                    twin = new ForkJoinPool(
                            pool.getParallelism(),
                            new RaisingForkJoinFactory(name, pool.getFactory()),
                            pool.getUncaughtExceptionHandler(),
                            pool.getAsyncMode()
                    );
                    TWINS.put(pool, twin);
                    LOGGER.debug(
                            "Replaced the {} pool with a normal-priority twin (parallelism {})",
                            name,
                            twin.getParallelism()
                    );
                }
                return twin;
            }
        }
        return executor;
    }

    /** Raises, never lowers, a freshly created worker and reports the first change per pool. */
    public static <T extends Thread> T raise(String name, T thread) {
        int before = thread.getPriority();
        if (before < TARGET_PRIORITY) {
            thread.setPriority(TARGET_PRIORITY);
            if (REPORTED.add(name)) {
                LOGGER.info(
                        "Restored {} thread priority {} -> {} lowered by Smooth Boot; thread counts unchanged",
                        name,
                        before,
                        TARGET_PRIORITY
                );
            }
        }
        return thread;
    }

    private record RaisingThreadFactory(String name, ThreadFactory delegate) implements ThreadFactory {
        @Override
        public Thread newThread(Runnable task) {
            return raise(name, delegate.newThread(task));
        }
    }

    private record RaisingForkJoinFactory(String name, ForkJoinPool.ForkJoinWorkerThreadFactory delegate)
            implements ForkJoinPool.ForkJoinWorkerThreadFactory {
        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return raise(name, delegate.newThread(pool));
        }
    }
}
