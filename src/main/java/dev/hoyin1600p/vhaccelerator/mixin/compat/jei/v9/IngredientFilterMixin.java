package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v9;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.ClientWorkSession;
import dev.hoyin1600p.vhaccelerator.client.PostLoginWorkTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.AdaptiveJeiWorkScheduler;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.DeferredIngredientMutations;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.InitialJeiVisibilityFastPath;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.JeiRecoveryReload;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.ParallelJeiPrefixIndexer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.overlay.IIngredientGridSource;
import mezz.jei.ingredients.IListElementInfo;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.ingredients.IngredientVisibility;
import mezz.jei.search.ElementPrefixParser;
import mezz.jei.search.ElementSearch;
import mezz.jei.search.IElementSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PlayerHeadItem;
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
 * Keeps JEI lifecycle and plugin callbacks serialized while moving only the
 * ingredient search-index build into an isolated worker-owned object.
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
    @Final
    private IngredientVisibility ingredientVisibility;

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
    @Unique
    private boolean vhaccelerator$visibilityStateChecked;
    @Unique
    private boolean vhaccelerator$initialItemsVisible;
    @Unique
    private int vhaccelerator$skippedVisibilityChecks;

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
                    target = "Lmezz/jei/search/IElementSearch;add(Lmezz/jei/ingredients/IListElementInfo;)V"
            )
    )
    private void vhaccelerator$journalSearchAddition(
            IElementSearch ignoredReceiver,
            IListElementInfo<?> info
    ) {
        if (vhaccelerator$constructing
                && vhaccelerator$optimizationsEnabled()
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

    @Redirect(
            method = "addIngredient",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/ingredients/IngredientFilter;updateHiddenState(Lmezz/jei/gui/ingredients/IListElement;)Z"
            )
    )
    private boolean vhaccelerator$skipRedundantInitialVisibility(
            IngredientFilter instance,
            IListElement<?> element
    ) {
        if (vhaccelerator$constructing
                && vhaccelerator$optimizationsEnabled()
                && VHAcceleratorClientConfig.VALUES
                        .optimizeJeiIngredientFilterConstruction
                        .get()
                && element.getTypedIngredient().getIngredient()
                        instanceof ItemStack
                && vhaccelerator$initialItemsAreVisible()) {
            vhaccelerator$skippedVisibilityChecks++;
            return false;
        }
        return instance.updateHiddenState(element);
    }

    @Redirect(
            method = "addIngredient",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/ingredients/IngredientFilter;invalidateCache()V"
            )
    )
    private void vhaccelerator$batchInitialInvalidation(
            IngredientFilter instance
    ) {
        if (vhaccelerator$constructing
                && vhaccelerator$optimizationsEnabled()
                && VHAcceleratorClientConfig.VALUES
                        .optimizeJeiIngredientFilterConstruction
                        .get()) {
            return;
        }
        instance.invalidateCache();
    }

    @Unique
    private boolean vhaccelerator$initialItemsAreVisible() {
        if (!vhaccelerator$visibilityStateChecked) {
            vhaccelerator$visibilityStateChecked = true;
            vhaccelerator$initialItemsVisible =
                    ingredientVisibility
                            instanceof InitialJeiVisibilityFastPath fastPath
                            && fastPath.vhaccelerator$hasNoHiddenIngredients();
        }
        return vhaccelerator$initialItemsVisible;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void vhaccelerator$startIsolatedIndexBuild(CallbackInfo ci) {
        if (vhaccelerator$optimizationsEnabled()
                && VHAcceleratorClientConfig.VALUES
                        .optimizeJeiIngredientFilterConstruction
                        .get()) {
            invalidateCache();
            if (vhaccelerator$skippedVisibilityChecks > 0) {
                VHAccelerator.LOGGER.info(
                        "Skipped {} redundant JEI 9 initial item-visibility "
                                + "UID checks and batched filter invalidation",
                        vhaccelerator$skippedVisibilityChecks
                );
            }
        }
        vhaccelerator$constructing = false;
        if (vhaccelerator$initialIngredients == null
                || vhaccelerator$initialIngredients.isEmpty()) {
            return;
        }

        List<IListElementInfo<?>> initial =
                List.copyOf(vhaccelerator$initialIngredients);
        List<IListElementInfo<?>> workerSafe = new ArrayList<>(initial.size());
        List<IListElementInfo<?>> dynamicPlayerHeads = new ArrayList<>();
        for (IListElementInfo<?> info : initial) {
            if (vhaccelerator$isDynamicPlayerHead(info)) {
                dynamicPlayerHeads.add(info);
            } else {
                workerSafe.add(info);
            }
        }
        vhaccelerator$initialIngredients = null;
        vhaccelerator$runtimeAdditions = new ArrayList<>();
        vhaccelerator$deferredMutations = new ArrayList<>();
        vhaccelerator$indexing = true;
        long sessionGeneration = ClientWorkSession.current();
        long workToken = PostLoginWorkTimer.markWorkStarted(
                sessionGeneration,
                "JEI 9 search index"
        );

        VHAccelerator.LOGGER.info(
                "Building an isolated JEI 9 search index for {} ingredients "
                        + "({} dynamic player-head ingredient(s) reserved for "
                        + "the client thread)",
                workerSafe.size(),
                dynamicPlayerHeads.size()
        );
        AdaptiveJeiWorkScheduler.submitIsolated(() -> {
            IElementSearch privateIndex = new ElementSearch(elementPrefixParser);
            if (VHAcceleratorClientConfig.VALUES.parallelJeiSearchPrefixes.get()
                    && AdaptiveJeiWorkScheduler.currentParallelism() > 1
                    && !workerSafe.isEmpty()) {
                ParallelJeiPrefixIndexer.populate(privateIndex, workerSafe);
            } else {
                workerSafe.forEach(privateIndex::add);
            }
            int indexedCount = privateIndex.getAllIngredients().size();
            if (indexedCount != workerSafe.size()) {
                throw new IllegalStateException(
                        "JEI 9 private index contains "
                                + indexedCount
                                + " of "
                                + workerSafe.size()
                                + " worker-safe ingredients"
                );
            }
            return privateIndex;
        }).whenComplete((privateIndex, failure) -> {
            if (!ClientWorkSession.isCurrent(sessionGeneration)) {
                vhaccelerator$cancelStaleBuild(workToken, sessionGeneration);
                return;
            }
            Minecraft.getInstance().execute(() ->
                    vhaccelerator$publishOrRecover(
                            initial,
                            dynamicPlayerHeads,
                            privateIndex,
                            failure,
                            workToken,
                            sessionGeneration
                    )
            );
        });
    }

    @Unique
    private void vhaccelerator$publishOrRecover(
            List<IListElementInfo<?>> initial,
            List<IListElementInfo<?>> dynamicPlayerHeads,
            IElementSearch privateIndex,
            Throwable failure,
            long workToken,
            long sessionGeneration
    ) {
        List<Runnable> deferredMutations;
        synchronized (vhaccelerator$indexLock) {
            if (!vhaccelerator$indexing
                    || !ClientWorkSession.isCurrent(sessionGeneration)) {
                vhaccelerator$indexing = false;
                vhaccelerator$runtimeAdditions.clear();
                vhaccelerator$deferredMutations.clear();
                PostLoginWorkTimer.cancel(workToken);
                return;
            }

            if (failure == null && privateIndex != null) {
                dynamicPlayerHeads.forEach(privateIndex::add);
                vhaccelerator$runtimeAdditions.forEach(privateIndex::add);
                int indexedCount = privateIndex.getAllIngredients().size();
                int expectedCount = initial.size()
                        + vhaccelerator$runtimeAdditions.size();
                if (indexedCount != expectedCount) {
                    failure = new IllegalStateException(
                            "JEI 9 completed index contains "
                                    + indexedCount
                                    + " of "
                                    + expectedCount
                                    + " ingredients"
                    );
                }
            }

            if (failure == null && privateIndex != null) {
                elementSearch = privateIndex;
                VHAccelerator.LOGGER.info(
                        "Published the complete JEI 9 search index with {} "
                                + "client-thread player head(s) and {} runtime "
                                + "addition(s)",
                        dynamicPlayerHeads.size(),
                        vhaccelerator$runtimeAdditions.size()
                );
            } else {
                VHAccelerator.LOGGER.warn(
                        "Isolated JEI 9 search indexing failed; rebuilding sequentially",
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
    private void vhaccelerator$cancelStaleBuild(
            long workToken,
            long sessionGeneration
    ) {
        synchronized (vhaccelerator$indexLock) {
            vhaccelerator$indexing = false;
            vhaccelerator$runtimeAdditions.clear();
            vhaccelerator$deferredMutations.clear();
        }
        PostLoginWorkTimer.cancel(workToken);
        VHAccelerator.LOGGER.info(
                "Discarded stale JEI 9 search-index callback for client "
                        + "session {}",
                sessionGeneration
        );
    }

    @Unique
    private static boolean vhaccelerator$isDynamicPlayerHead(
            IListElementInfo<?> info
    ) {
        Object ingredient = info.getTypedIngredient().getIngredient();
        if (!(ingredient instanceof ItemStack stack)) {
            return false;
        }
        return stack.getItem() instanceof PlayerHeadItem
                || stack.hasTag()
                && (stack.getTag().contains("SkullOwner")
                || stack.getTag().contains("ExtraType"));
    }

    @Unique
    private void vhaccelerator$replayMutations(List<Runnable> mutations) {
        for (Runnable mutation : mutations) {
            try {
                mutation.run();
            } catch (Throwable failure) {
                VHAccelerator.LOGGER.error(
                        "A deferred JEI 9 ingredient mutation failed",
                        failure
                );
            }
        }
        if (!mutations.isEmpty()) {
            VHAccelerator.LOGGER.info(
                    "Replayed {} deferred JEI 9 ingredient mutations",
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
        if (!vhaccelerator$optimizationsEnabled()
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
                    "Sorting {} JEI 9 ingredients with {} adaptive workers",
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
                    "Adaptive JEI 9 ingredient sorting failed; retrying sequentially",
                    exception
            );
        }
    }

    @Unique
    private static boolean vhaccelerator$optimizationsEnabled() {
        return JeiRecoveryReload.optimizationsAllowed()
                && VHAcceleratorClientConfig.optimizationsEnabled();
    }
}
