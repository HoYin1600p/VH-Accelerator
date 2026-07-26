package dev.hoyin1600p.launchfastertoo.mixin.compat.jeitweaker;

import com.blamejared.jeitweaker.api.IngredientType;
import com.blamejared.jeitweaker.implementation.state.StateManager;
import com.blamejared.jeitweaker.jei.JeiTweakerPlugin;
import dev.hoyin1600p.launchfastertoo.LaunchFasterToo;
import dev.hoyin1600p.launchfastertoo.client.LaunchFasterTooClientConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Parallelizes only matching over immutable snapshots. JEI mutation remains on
 * the lifecycle caller, and a failed parallel pass is retried sequentially.
 */
@Mixin(value = JeiTweakerPlugin.class, remap = false)
public abstract class JeiTweakerPluginMixin {
    private static final int WORKERS = Math.max(
            1,
            Math.min(4, Runtime.getRuntime().availableProcessors() - 1)
    );
    private static final ForkJoinPool MATCH_POOL = new ForkJoinPool(
            WORKERS,
            pool -> {
                ForkJoinPool.ForkJoinWorkerThreadFactory factory =
                        ForkJoinPool.defaultForkJoinWorkerThreadFactory;
                java.util.concurrent.ForkJoinWorkerThread worker = factory.newThread(pool);
                worker.setName("LaunchFasterToo-JEITweaker-" + worker.getPoolIndex());
                worker.setDaemon(true);
                return worker;
            },
            (thread, throwable) -> LaunchFasterToo.LOGGER.error(
                    "Uncaught JEITweaker matching failure on {}", thread.getName(), throwable
            ),
            true
    );

    @Inject(method = "hideIngredientsFor", at = @At("HEAD"), cancellable = true)
    private <T, U> void launchfastertoo$matchHiddenIngredients(
            IIngredientManager manager,
            IngredientType<T, U> type,
            CallbackInfo ci
    ) {
        if (!LaunchFasterTooClientConfig.VALUES.enableClientOptimizations.get()
                || !LaunchFasterTooClientConfig.VALUES.parallelJeiTweakerMatching.get()) {
            return;
        }

        ci.cancel();
        Collection<T> configured = StateManager.INSTANCE.actionsState().getHiddenIngredientsForType(type);
        if (configured.isEmpty()) {
            return;
        }

        IIngredientType<U> jeiType = type.toJeiIngredientType(manager);
        List<U> available = List.copyOf(manager.getAllIngredients(jeiType));
        List<T> hidden = List.copyOf(configured);
        List<U> removals;

        int threshold = LaunchFasterTooClientConfig.VALUES.jeiTweakerParallelThreshold.get();
        if (available.size() >= threshold && WORKERS > 1) {
            try {
                removals = MATCH_POOL.submit(
                        () -> available.parallelStream()
                                .map(type::toJeiTweakerType)
                                .filter(candidate -> launchfastertoo$matches(type, hidden, candidate))
                                .map(type::toJeiType)
                                .toList()
                ).join();
            } catch (RuntimeException exception) {
                LaunchFasterToo.LOGGER.warn(
                        "Parallel JEITweaker matching failed for {}; retrying synchronously",
                        type.id(),
                        exception
                );
                removals = launchfastertoo$matchSequentially(type, hidden, available);
            }
        } else {
            removals = launchfastertoo$matchSequentially(type, hidden, available);
        }

        if (!removals.isEmpty()) {
            manager.removeIngredientsAtRuntime(jeiType, removals);
        }
    }

    private static <T, U> boolean launchfastertoo$matches(
            IngredientType<T, U> type,
            List<T> hidden,
            T candidate
    ) {
        for (T configured : hidden) {
            if (type.match(configured, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static <T, U> List<U> launchfastertoo$matchSequentially(
            IngredientType<T, U> type,
            List<T> hidden,
            List<U> available
    ) {
        List<U> removals = new ArrayList<>();
        for (U ingredient : available) {
            T candidate = type.toJeiTweakerType(ingredient);
            if (launchfastertoo$matches(type, hidden, candidate)) {
                removals.add(type.toJeiType(candidate));
            }
        }
        return removals;
    }
}
