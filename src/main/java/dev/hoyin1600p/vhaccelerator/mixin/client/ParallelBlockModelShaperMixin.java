package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockModelShaper.class)
public abstract class ParallelBlockModelShaperMixin {
    private static final int MIN_STATES_PER_WORKER = 4_096;

    @Shadow
    @Final
    private ModelManager modelManager;

    @Shadow
    @Final
    @Mutable
    private Map<BlockState, BakedModel> modelByStateCache;

    @Inject(
            method = "rebuildCache",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vhaccelerator$buildLookupInParallel(
            CallbackInfo callback
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.parallelBlockModelCache
                )) {
            return;
        }

        long started = System.nanoTime();
        try {
            BlockState[] states = vhaccelerator$collectStates();
            BakedModel[] models = new BakedModel[states.length];
            int workers = vhaccelerator$workerCount(states.length);
            int batchSize = Math.max(
                    1,
                    (states.length + workers - 1) / workers
            );
            List<CompletableFuture<Void>> tasks =
                    new ArrayList<>(workers);

            for (int start = 0;
                 start < states.length;
                 start += batchSize) {
                int from = start;
                int to = Math.min(
                        start + batchSize,
                        states.length
                );
                tasks.add(CompletableFuture.runAsync(() -> {
                    for (int index = from; index < to; index++) {
                        models[index] = modelManager.getModel(
                                BlockModelShaper
                                        .stateToModelLocation(
                                                states[index]
                                        )
                        );
                    }
                }, Util.backgroundExecutor()));
            }
            CompletableFuture.allOf(
                    tasks.toArray(CompletableFuture[]::new)
            ).join();

            Map<BlockState, BakedModel> complete =
                    new IdentityHashMap<>(states.length);
            for (int index = 0; index < states.length; index++) {
                complete.put(states[index], models[index]);
            }
            modelByStateCache = complete;
            callback.cancel();

            VHAccelerator.LOGGER.info(
                    "Built {} block model render lookups with {} "
                            + "workers in {} ms",
                    states.length,
                    tasks.size(),
                    (System.nanoTime() - started) / 1_000_000L
            );
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.warn(
                    "Parallel block model lookup failed after {} ms; "
                            + "retrying Minecraft's original pass",
                    (System.nanoTime() - started) / 1_000_000L,
                    failure
            );
        }
    }

    private static BlockState[] vhaccelerator$collectStates() {
        List<BlockState> states = new ArrayList<>();
        for (Block block : Registry.BLOCK) {
            states.addAll(
                    block.getStateDefinition().getPossibleStates()
            );
        }
        return states.toArray(BlockState[]::new);
    }

    private static int vhaccelerator$workerCount(
            int stateCount
    ) {
        int usefulWorkers = Math.max(
                1,
                (stateCount + MIN_STATES_PER_WORKER - 1)
                        / MIN_STATES_PER_WORKER
        );
        return Math.max(
                1,
                Math.min(
                        Runtime.getRuntime().availableProcessors(),
                        usefulWorkers
                )
        );
    }
}
