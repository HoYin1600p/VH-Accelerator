package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.model.ModelPreparationCoordinator;
import dev.hoyin1600p.vhaccelerator.client.model.ModelPreparationWorkHolder;
import dev.hoyin1600p.vhaccelerator.client.model.ParallelBlockStateJsonParser;
import dev.hoyin1600p.vhaccelerator.client.model.ParallelBlockStateModelLocations;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelBakery.class, priority = 1_500)
public abstract class ModelBakeryPreparationStartMixin
        implements ModelPreparationWorkHolder {
    @Shadow
    @Final
    protected ResourceManager resourceManager;

    @Unique
    private boolean vhaccelerator$overlappedPreparation;
    @Unique
    @Nullable
    private CompletableFuture<Void>
            vhaccelerator$modelLocationFuture;
    @Unique
    @Nullable
    private CompletableFuture<
            ParallelBlockStateJsonParser.Session>
            vhaccelerator$blockStateFuture;

    @Inject(method = "processLoading", at = @At("HEAD"), remap = false)
    private void vhaccelerator$startIndependentPreparation(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$startModelPreparation(resourceManager);
    }

    @Inject(method = "processLoading", at = @At("TAIL"), remap = false)
    private void vhaccelerator$releasePreparationFutures(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$modelLocationFuture = null;
        vhaccelerator$blockStateFuture = null;
    }

    @Override
    public void vhaccelerator$startModelPreparation(
            ResourceManager resourceManager
    ) {
        vhaccelerator$overlappedPreparation =
                VHAcceleratorClientConfig.optimizationsEnabled()
                        && VHAcceleratorClientConfig.launchValue(
                                VHAcceleratorClientConfig.VALUES.overlapModelPreparation
                        )
                        && ModelPreparationCoordinator.hasBackgroundCapacity();
        if (!vhaccelerator$overlappedPreparation) {
            return;
        }
        vhaccelerator$modelLocationFuture =
                CompletableFuture.runAsync(
                        ParallelBlockStateModelLocations::prepare,
                        ModelPreparationCoordinator.executor()
                );
        vhaccelerator$blockStateFuture =
                CompletableFuture.supplyAsync(
                        () -> ParallelBlockStateJsonParser.prepare(
                                resourceManager
                        ),
                        ModelPreparationCoordinator.executor()
                );
    }

    @Override
    public boolean vhaccelerator$hasOverlappedPreparation() {
        return vhaccelerator$overlappedPreparation;
    }

    @Override
    public void vhaccelerator$awaitModelLocations() {
        CompletableFuture<Void> future =
                vhaccelerator$modelLocationFuture;
        vhaccelerator$modelLocationFuture = null;
        if (future == null) {
            return;
        }
        try {
            future.join();
        } catch (RuntimeException failure) {
            VHAccelerator.LOGGER.warn(
                    "Overlapped block-state model-key preparation "
                            + "failed; Minecraft will create missing "
                            + "keys during discovery",
                    failure
            );
        }
    }

    @Override
    @Nullable
    public ParallelBlockStateJsonParser.Session
            vhaccelerator$awaitBlockStates() {
        CompletableFuture<
                ParallelBlockStateJsonParser.Session> future =
                vhaccelerator$blockStateFuture;
        vhaccelerator$blockStateFuture = null;
        if (future == null) {
            return null;
        }
        try {
            return future.join();
        } catch (RuntimeException failure) {
            VHAccelerator.LOGGER.warn(
                    "Overlapped blockstate preparation failed; "
                            + "Minecraft will use its original loader",
                    failure
            );
            return null;
        }
    }

}
