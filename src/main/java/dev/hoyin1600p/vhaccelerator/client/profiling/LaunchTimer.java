package dev.hoyin1600p.vhaccelerator.client.profiling;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.backport.BackportOwnershipRegistry;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.load.ResponsiveModWorkQueue;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.registry.ResourceLocationNamespaces;
import dev.hoyin1600p.vhaccelerator.client.diagnostics.BakedModelCensus;
import dev.hoyin1600p.vhaccelerator.client.model.BakeryRetention;
import dev.hoyin1600p.vhaccelerator.client.model.UnbakedCacheCensus;
import dev.hoyin1600p.vhaccelerator.client.model.parse.ParallelBlockStateJsonParser;
import dev.hoyin1600p.vhaccelerator.compat.decocraft.DecocraftModelData;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class LaunchTimer {
    private static final AtomicLong START_NANOS = new AtomicLong(-1L);
    private static final AtomicLong END_NANOS = new AtomicLong(-1L);
    private static final AtomicBoolean CHAT_MESSAGE_CLAIMED = new AtomicBoolean();

    private LaunchTimer() {
    }

    public static void markStart() {
        long attachedAtNanos = System.nanoTime();
        long processUptimeMillis = Math.max(
                0L,
                ManagementFactory.getRuntimeMXBean().getUptime()
        );
        long processStartNanos = attachedAtNanos
                - TimeUnit.MILLISECONDS.toNanos(processUptimeMillis);
        if (START_NANOS.compareAndSet(-1L, processStartNanos)) {
            if (VHAcceleratorConfig.instrumentationEnabled()) {
                VHAccelerator.LOGGER.info(
                        "Client launch timer attached {} ms after JVM "
                                + "process start",
                        processUptimeMillis
                );
            }
        }
    }

    public static void markEnd() {
        long start = START_NANOS.get();
        if (start < 0L || !END_NANOS.compareAndSet(-1L, System.nanoTime())) {
            return;
        }

        long elapsedMillis = elapsedMillis();
        if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            RegistryLaunchProfiler.finish();
            LaunchEventProfiler.finish();
            LaunchStackSampler.stopAndReport();
            BakeryRetention.report(net.minecraftforge.client.model.ForgeModelBakery.instance());
            if ((Object) net.minecraftforge.client.model.ForgeModelBakery.instance() instanceof BakeryRetention census) {
                // Diagnostics must never take the client down.
                try {
                    BakedModelCensus.report(
                            census.vhaccelerator$bakedTopLevelModels());
                } catch (RuntimeException | AssertionError | LinkageError failure) {
                    VHAccelerator.LOGGER.warn("[debug] Baked model census failed", failure);
                }
                try {
                    UnbakedCacheCensus.report(
                            census.vhaccelerator$unbakedCache());
                } catch (RuntimeException | AssertionError | LinkageError failure) {
                    VHAccelerator.LOGGER.warn("[debug] Unbaked cache census failed", failure);
                }
            }
            VHAccelerator.LOGGER.info("[debug] Mod-loading work queue: {}", ResponsiveModWorkQueue.describe());
            if (BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.RESOURCE_LOCATION_NAMESPACE_DEDUPLICATION
            )) {
                long duplicates = ResourceLocationNamespaces.deduplicated();
                VHAccelerator.LOGGER.info(
                        "[debug] ResourceLocation namespaces: {} pooled, {} "
                                + "duplicate strings replaced since mod "
                                + "construction (~{} KiB at ~56 B each)",
                        ResourceLocationNamespaces.pooled(),
                        duplicates,
                        duplicates * 56L / 1024L
                );
            }
        }
        if (VHAcceleratorConfig.instrumentationEnabled()) {
            VHAccelerator.LOGGER.info(
                    "Client launch completed in {} ms ({})",
                    elapsedMillis,
                    String.format(
                            "%.2f seconds",
                            elapsedMillis / 1000.0
                    )
            );
        }
        DecocraftModelData.report();
        ParallelBlockStateJsonParser.releaseLaunchSessions();
    }

    public static long elapsedMillis() {
        long start = START_NANOS.get();
        if (start < 0L) {
            return -1L;
        }
        long end = END_NANOS.get();
        return ((end < 0L ? System.nanoTime() : end) - start) / 1_000_000L;
    }

    public static boolean isFinished() {
        return END_NANOS.get() >= 0L;
    }

    public static boolean claimChatMessage() {
        return isFinished()
                && VHAcceleratorConfig.timersEnabled()
                && CHAT_MESSAGE_CLAIMED.compareAndSet(false, true);
    }
}
