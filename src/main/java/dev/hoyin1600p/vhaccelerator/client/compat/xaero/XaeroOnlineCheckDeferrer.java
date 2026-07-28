package dev.hoyin1600p.vhaccelerator.client.compat.xaero;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;

/**
 * Releases Xaero's nonessential online checks only after the client has
 * rendered a usable menu or world frame.
 */
public final class XaeroOnlineCheckDeferrer {
    private static final Queue<DeferredCheck> QUEUED =
            new ConcurrentLinkedQueue<>();
    private static final Set<String> REGISTERED =
            ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean RELEASED = new AtomicBoolean();

    private XaeroOnlineCheckDeferrer() {
    }

    public static boolean enabled() {
        return VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .deferXaeroOnlineChecks
                );
    }

    public static void defer(
            String name,
            Runnable onlineCheck,
            Runnable applyResult
    ) {
        if (!REGISTERED.add(name)) {
            VHAccelerator.LOGGER.debug(
                    "Ignored duplicate deferred Xaero online check for {}",
                    name
            );
            return;
        }

        DeferredCheck check = new DeferredCheck(
                name,
                onlineCheck,
                applyResult
        );
        if (RELEASED.get()) {
            submit(check);
            return;
        }

        QUEUED.add(check);
        VHAccelerator.LOGGER.info(
                "Deferred {} online checks until after the first usable frame",
                name
        );
        if (RELEASED.get() && QUEUED.remove(check)) {
            submit(check);
        }
    }

    public static void releaseAfterUsableFrame() {
        if (!RELEASED.compareAndSet(false, true)) {
            return;
        }

        int count = QUEUED.size();
        if (count == 0) {
            return;
        }
        VHAccelerator.LOGGER.info(
                "Releasing {} deferred Xaero online check group(s)",
                count
        );
        DeferredCheck check;
        while ((check = QUEUED.poll()) != null) {
            submit(check);
        }
    }

    private static void submit(DeferredCheck check) {
        ExecutorHolder.EXECUTOR.execute(() -> run(check));
    }

    private static void run(DeferredCheck check) {
        long startedNanos = System.nanoTime();
        try {
            check.onlineCheck().run();
        } catch (Throwable failure) {
            VHAccelerator.LOGGER.warn(
                    "Deferred {} online checks failed; core map functionality is unaffected",
                    check.name(),
                    failure
            );
            return;
        }

        long elapsedMillis =
                (System.nanoTime() - startedNanos) / 1_000_000L;
        VHAccelerator.LOGGER.info(
                "Deferred {} online checks finished in {} ms",
                check.name(),
                elapsedMillis
        );
        Minecraft.getInstance().execute(() -> {
            try {
                check.applyResult().run();
            } catch (Throwable failure) {
                VHAccelerator.LOGGER.warn(
                        "Could not apply deferred {} update metadata; core map functionality is unaffected",
                        check.name(),
                        failure
                );
            }
        });
    }

    private record DeferredCheck(
            String name,
            Runnable onlineCheck,
            Runnable applyResult
    ) {
    }

    private static final class ExecutorHolder {
        private static final ExecutorService EXECUTOR =
                Executors.newSingleThreadExecutor(new XaeroThreadFactory());

        private ExecutorHolder() {
        }
    }

    private static final class XaeroThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(
                    task,
                    "VH Accelerator Xaero online checks"
            );
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }
    }
}
