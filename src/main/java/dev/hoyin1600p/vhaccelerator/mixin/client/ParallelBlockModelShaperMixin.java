package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.model.DeferredBlockStateBaking;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
        try {
            Map<BlockState, BakedModel> lazy =
                    DeferredBlockStateBaking.buildShaperCache(modelManager);
            if (lazy != null) {
                modelByStateCache = lazy;
                callback.cancel();
                return;
            }
        } catch (RuntimeException | LinkageError failure) {
            // The lookup below reads every model, baking any deferred ones.
            VHAccelerator.LOGGER.warn(
                    "Deferred block model lookup failed; building it eagerly",
                    failure
            );
        }
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
            SharedWorkers.forRange(states.length, index -> models[index] = modelManager.getModel(
                    BlockModelShaper.stateToModelLocation(states[index])));

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
                    workers,
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
        return Math.max(1, Math.min(SharedWorkers.budget().compute(), stateCount));
    }
}
