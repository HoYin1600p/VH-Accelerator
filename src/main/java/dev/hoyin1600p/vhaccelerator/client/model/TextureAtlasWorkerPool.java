package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps TextureAtlas child work off the resource-reload pool that waits for it.
 *
 * <p>Vanilla prepares atlases on resource-reload workers, submits every texture
 * metadata and PNG-header job to Minecraft's shared background pool, and joins
 * those children. Large packs can occupy that pool with the parent reload jobs,
 * leaving even tiny atlases waiting for an available worker. A separate bounded
 * pool removes that nested-executor starvation without changing texture decode,
 * stitch, or publication logic.</p>
 */
public final class TextureAtlasWorkerPool {
    private static final int MAX_WORKERS = 16;
    private static final int WORKERS = recommendedWorkerCount(
            Runtime.getRuntime().availableProcessors()
    );
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private TextureAtlasWorkerPool() {
    }

    public static Executor select(Executor vanillaExecutor) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .parallelAtlasStitching
                )) {
            return vanillaExecutor;
        }
        if (REPORTED.compareAndSet(false, true)) {
            VHAccelerator.LOGGER.info(
                    "Using {} dedicated texture metadata worker(s) to avoid nested "
                            + "resource-reload starvation",
                    WORKERS
            );
        }
        return Holder.EXECUTOR;
    }

    public static int recommendedWorkerCount(int availableProcessors) {
        int processors = Math.max(1, availableProcessors);
        return Math.max(1, Math.min(MAX_WORKERS, processors / 2));
    }

    private static final class Holder {
        private static final ExecutorService EXECUTOR =
                Executors.newFixedThreadPool(
                        WORKERS,
                        new TextureThreadFactory()
                );

        private Holder() {
        }
    }

    private static final class TextureThreadFactory
            implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(
                    task,
                    "VH Accelerator texture worker-"
                            + sequence.incrementAndGet()
            );
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
