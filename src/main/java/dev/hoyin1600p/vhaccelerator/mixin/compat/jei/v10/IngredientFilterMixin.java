package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v10;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.PostLoginWorkTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.AdaptiveJeiWorkScheduler;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.DeferredIngredientMutations;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.ParallelJeiPrefixIndexer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import mezz.jei.common.gui.overlay.IIngredientGridSource;
import mezz.jei.common.ingredients.IListElementInfo;
import mezz.jei.common.ingredients.IngredientFilter;
import mezz.jei.common.search.ElementPrefixParser;
import mezz.jei.common.search.ElementSearch;
import mezz.jei.common.search.IElementSearch;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JEI 10 counterpart to the JEI 9 isolated search-index implementation.
 */
@Pseudo
@Mixin(value = IngredientFilter.class, remap = false)
public abstract class IngredientFilterMixin implements DeferredIngredientMutations {
    @Shadow
    @Final
    @Mutable
    private IElementSearch elementSearch;

    @Shadow
    @Final
    private ElementPrefixParser elementPrefixParser;

    @Shadow
    @Final
    private List<IIngredientGridSource.SourceListChangedListener> listeners;

    @Shadow
    public abstract void invalidateCache();

    @Unique
    private final Object vhaccelerator$indexLock = new Object();
    @Unique
    private boolean vhaccelerator$constructing = true;
    @Unique
    private volatile boolean vhaccelerator$indexing;
    @Unique
    private List<IListElementInfo<?>> vhaccelerator$initialIngredients;
    @Unique
    private List<IListElementInfo<?>> vhaccelerator$runtimeAdditions;
    @Unique
    private List<Runnable> vhaccelerator$deferredMutations;

    @Override
    public boolean vhaccelerator$deferIngredientMutation(Runnable mutation) {
        synchronized (vhaccelerator$indexLock) {
            if (!vhaccelerator$indexing) {
                return false;
            }
            vhaccelerator$deferredMutations.add(mutation);
            return true;
        }
    }

    @Redirect(
            method = "addIngredient",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/common/search/IElementSearch;add(Lmezz/jei/common/ingredients/IListElementInfo;)V"
            )
    )
    private void vhaccelerator$journalSearchAddition(
            IElementSearch ignoredReceiver,
            IListElementInfo<?> info
    ) {
        if (vhaccelerator$constructing
                && VHAcceleratorClientConfig.VALUES.enableClientOptimizations.get()
                && VHAcceleratorClientConfig.VALUES.asyncJeiSearchIndex.get()
                && ignoredReceiver instanceof ElementSearch) {
            if (vhaccelerator$initialIngredients == null) {
                vhaccelerator$initialIngredients = new ArrayList<>();
            }
            vhaccelerator$initialIngredients.add(info);
            return;
        }

        synchronized (vhaccelerator$indexLock) {
            elementSearch.add(info);
            if (vhaccelerator$indexing) {
                vhaccelerator$runtimeAdditions.add(info);
            }
        }
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void vhaccelerator$startIsolatedIndexBuild(CallbackInfo ci) {
        vhaccelerator$constructing = false;
        if (vhaccelerator$initialIngredients == null
                || vhaccelerator$initialIngredients.isEmpty()) {
            return;
        }

        List<IListElementInfo<?>> initial =
                List.copyOf(vhaccelerator$initialIngredients);
        vhaccelerator$initialIngredients = null;
        vhaccelerator$runtimeAdditions = new ArrayList<>();
        vhaccelerator$deferredMutations = new ArrayList<>();
        vhaccelerator$indexing = true;
        long workToken = PostLoginWorkTimer.markWorkStarted();

        VHAccelerator.LOGGER.info(
                "Building an isolated JEI 10 search index for {} ingredients",
                initial.size()
        );
        AdaptiveJeiWorkScheduler.submitIsolated(() -> {
            IElementSearch privateIndex = new ElementSearch(elementPrefixParser);
            if (VHAcceleratorClientConfig.VALUES.parallelJeiSearchPrefixes.get()
                    && AdaptiveJeiWorkScheduler.currentParallelism() > 1) {
                ParallelJeiPrefixIndexer.populate(privateIndex, initial);
            } else {
                initial.forEach(privateIndex::add);
            }
            int indexedCount = privateIndex.getAllIngredients().size();
            if (indexedCount != initial.size()) {
                throw new IllegalStateException(
                        "JEI 10 private index contains "
                                + indexedCount
                                + " of "
                                + initial.size()
                                + " ingredients"
                );
            }
            return privateIndex;
        }).whenComplete((privateIndex, failure) ->
                Minecraft.getInstance().execute(() ->
                        vhaccelerator$publishOrRecover(
                                initial,
                                privateIndex,
                                failure,
                                workToken
                        )
                )
        );
    }

    @Unique
    private void vhaccelerator$publishOrRecover(
            List<IListElementInfo<?>> initial,
            IElementSearch privateIndex,
            Throwable failure,
            long workToken
    ) {
        List<Runnable> deferredMutations;
        synchronized (vhaccelerator$indexLock) {
            if (!vhaccelerator$indexing) {
                PostLoginWorkTimer.cancel(workToken);
                return;
            }

            if (failure == null && privateIndex != null) {
                vhaccelerator$runtimeAdditions.forEach(privateIndex::add);
                elementSearch = privateIndex;
                VHAccelerator.LOGGER.info(
                        "Published the complete JEI 10 search index with {} runtime additions",
                        vhaccelerator$runtimeAdditions.size()
                );
            } else {
                VHAccelerator.LOGGER.warn(
                        "Isolated JEI 10 search indexing failed; rebuilding sequentially",
                        failure
                );
                initial.forEach(elementSearch::add);
            }

            vhaccelerator$indexing = false;
            vhaccelerator$runtimeAdditions.clear();
            deferredMutations = List.copyOf(vhaccelerator$deferredMutations);
            vhaccelerator$deferredMutations.clear();
        }

        vhaccelerator$replayMutations(deferredMutations);
        invalidateCache();
        listeners.forEach(IIngredientGridSource.SourceListChangedListener::onSourceListChanged);
        PostLoginWorkTimer.markWorkCompleted(workToken);
    }

    @Unique
    private void vhaccelerator$replayMutations(List<Runnable> mutations) {
        for (Runnable mutation : mutations) {
            try {
                mutation.run();
            } catch (Throwable failure) {
                VHAccelerator.LOGGER.error(
                        "A deferred JEI 10 ingredient mutation failed",
                        failure
                );
            }
        }
        if (!mutations.isEmpty()) {
            VHAccelerator.LOGGER.info(
                    "Replayed {} deferred JEI 10 ingredient mutations",
                    mutations.size()
            );
        }
    }

    @Inject(
            method = "getIngredientListPreSort",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vhaccelerator$sortOnAdaptivePool(
            Comparator<IListElementInfo<?>> comparator,
            CallbackInfoReturnable<List<IListElementInfo<?>>> cir
    ) {
        if (!VHAcceleratorClientConfig.VALUES.enableClientOptimizations.get()
                || !VHAcceleratorClientConfig.VALUES.parallelJeiIngredientSorting.get()
                || vhaccelerator$indexing) {
            return;
        }

        Collection<IListElementInfo<?>> ingredients = elementSearch.getAllIngredients();
        if (ingredients.size() < 512
                || AdaptiveJeiWorkScheduler.currentParallelism() <= 1) {
            return;
        }

        try {
            VHAccelerator.LOGGER.info(
                    "Sorting {} JEI 10 ingredients with {} adaptive workers",
                    ingredients.size(),
                    AdaptiveJeiWorkScheduler.currentParallelism()
            );
            List<IListElementInfo<?>> sorted =
                    AdaptiveJeiWorkScheduler.invokeParallel(
                            () -> ingredients.parallelStream().sorted(comparator).toList()
                    );
            cir.setReturnValue(sorted);
        } catch (RuntimeException exception) {
            VHAccelerator.LOGGER.warn(
                    "Adaptive JEI 10 ingredient sorting failed; retrying sequentially",
                    exception
            );
        }
    }
}
