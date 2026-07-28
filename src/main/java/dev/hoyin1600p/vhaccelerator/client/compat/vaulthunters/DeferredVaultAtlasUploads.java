package dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.client.LaunchTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * Uses the otherwise fixed loading-overlay fade to finish Vault's independent
 * GUI atlas uploads after the reload barrier.
 */
public final class DeferredVaultAtlasUploads {
    private static final Queue<PendingUpload> PENDING =
            new ConcurrentLinkedQueue<>();
    private static final AtomicInteger QUEUED_COUNT = new AtomicInteger();
    private static final AtomicInteger COMPLETED_COUNT = new AtomicInteger();
    private static final AtomicLong UPLOAD_NANOS = new AtomicLong();
    private static final AtomicLong FIRST_UPLOAD_NANOS =
            new AtomicLong(-1L);

    private DeferredVaultAtlasUploads() {
    }

    public static boolean validationOptimizationEnabled() {
        return VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .optimizeVaultAtlasValidation
                );
    }

    public static boolean validationDiagnosticsEnabled() {
        return VHAcceleratorConfig.debugDiagnosticsEnabled();
    }

    public static boolean defer(
            DeferredVaultAtlasUpload target,
            TextureAtlas.Preparations preparations,
            ResourceLocation atlasLocation
    ) {
        if (LaunchTimer.isFinished()
                || !VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .deferVaultAtlasUploads
                )) {
            return false;
        }

        PENDING.add(new PendingUpload(
                target,
                preparations,
                atlasLocation
        ));
        int queued = QUEUED_COUNT.incrementAndGet();
        VHAccelerator.LOGGER.debug(
                "Queued Vault atlas {} for loading-overlay upload [{} queued]",
                atlasLocation,
                queued
        );
        return true;
    }

    public static boolean hasPendingUploads() {
        return !PENDING.isEmpty();
    }

    public static void processLoadingOverlayFrame() {
        PendingUpload pending = PENDING.poll();
        if (pending == null) {
            return;
        }

        long firstStarted = FIRST_UPLOAD_NANOS.get();
        if (firstStarted < 0L) {
            long now = System.nanoTime();
            FIRST_UPLOAD_NANOS.compareAndSet(-1L, now);
            VHAccelerator.LOGGER.info(
                    "Starting {} deferred Vault atlas upload(s) during the loading fade",
                    QUEUED_COUNT.get()
            );
        }

        long started = System.nanoTime();
        try {
            pending.target().vhaccelerator$uploadVaultAtlas(
                    pending.preparations()
            );
        } catch (Throwable failure) {
            VHAccelerator.LOGGER.error(
                    "Deferred Vault atlas upload failed for {}",
                    pending.atlasLocation(),
                    failure
            );
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(failure);
        } finally {
            UPLOAD_NANOS.addAndGet(System.nanoTime() - started);
        }

        int completed = COMPLETED_COUNT.incrementAndGet();
        if (PENDING.isEmpty()) {
            long elapsedNanos =
                    System.nanoTime() - FIRST_UPLOAD_NANOS.get();
            VHAccelerator.LOGGER.info(
                    "Completed {} deferred Vault atlas upload(s) in {} ms of work over {} ms of loading-overlay time",
                    completed,
                    UPLOAD_NANOS.get() / 1_000_000L,
                    elapsedNanos / 1_000_000L
            );
        }
    }

    private record PendingUpload(
            DeferredVaultAtlasUpload target,
            TextureAtlas.Preparations preparations,
            ResourceLocation atlasLocation
    ) {
    }
}
