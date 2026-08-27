package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v9;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.LoginStateFingerprint;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.CachedRecipeOutputReconciler;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.PersistentJeiRecipeIndexCache;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.JeiRecoveryReload;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.v9.RecipeMapIndexAccess;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.ingredients.IIngredientSupplier;
import mezz.jei.ingredients.RegisteredIngredients;
import mezz.jei.recipes.IngredientSupplierHelper;
import mezz.jei.recipes.RecipeManagerInternal;
import mezz.jei.recipes.RecipeMap;
import mezz.jei.recipes.RecipeTypeData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = RecipeManagerInternal.class, remap = false)
public abstract class RecipeManagerInternalIndexMixin {
    @Unique
    private static final int VHACCELERATOR$MINIMUM_RECIPES = 128;

    @Shadow
    @Final
    private RegisteredIngredients registeredIngredients;

    @Shadow
    @Final
    private EnumMap<RecipeIngredientRole, RecipeMap> recipeMaps;

    @Shadow
    private List<IRecipeCategory<?>> recipeCategoriesVisibleCache;

    @Inject(
            method = "addRecipes(Lmezz/jei/recipes/RecipeTypeData;"
                    + "Ljava/util/Collection;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T> void vhaccelerator$restoreOrBuildRecipeIndex(
            RecipeTypeData<T> recipeTypeData,
            Collection<T> recipes,
            CallbackInfo callback
    ) {
        IRecipeCategory<T> category =
                recipeTypeData.getRecipeCategory();
        ResourceLocation categoryUid =
                category.getRecipeType().getUid();
        if (!vhaccelerator$eligible(
                categoryUid,
                recipeTypeData,
                recipes
        )) {
            return;
        }
        LoginStateFingerprint.Snapshot fingerprint =
                LoginStateFingerprint.currentWithRecipes();
        if (fingerprint == null) {
            return;
        }

        long started = System.nanoTime();
        PersistentJeiRecipeIndexCache.RestoreResult<T> restored =
                PersistentJeiRecipeIndexCache.restore(
                        fingerprint,
                        "jei9",
                        categoryUid.toString(),
                        recipes
                );
        if (restored != null) {
            PersistentJeiRecipeIndexCache.ReconciledPlans<T> reconciled;
            try {
                reconciled = vhaccelerator$reconcile(
                        category,
                        recipes,
                        restored.recipes()
                );
            } catch (RuntimeException | LinkageError failure) {
                VHAccelerator.LOGGER.warn(
                        "Could not reconcile the cached JEI 9 {} recipe "
                                + "index; running JEI's original indexer",
                        categoryUid,
                        failure
                );
                return;
            }
            vhaccelerator$apply(
                    category.getRecipeType(),
                    recipeTypeData,
                    reconciled.plans()
            );
            if (reconciled.rebuiltCount() > 0
                    || reconciled.cachedCount()
                            != restored.cachedRecipeCount()) {
                PersistentJeiRecipeIndexCache.record(
                        fingerprint,
                        "jei9",
                        categoryUid.toString(),
                        recipes,
                        reconciled.plans()
                );
            }
            VHAccelerator.LOGGER.info(
                    "Restored {} of {} cached JEI 9 {} recipe index plans "
                            + "and rebuilt {} live plan(s) in {} ms",
                    reconciled.cachedCount(),
                    restored.cachedRecipeCount(),
                    categoryUid,
                    reconciled.rebuiltCount(),
                    (System.nanoTime() - started) / 1_000_000L
            );
            callback.cancel();
            return;
        }

        List<PersistentJeiRecipeIndexCache.ActiveRecipe<T>>
                prepared;
        try {
            prepared = vhaccelerator$prepare(category, recipes);
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not prepare the JEI 9 {} recipe index cache; "
                            + "running JEI's original indexer",
                    categoryUid,
                    failure
            );
            return;
        }
        vhaccelerator$apply(
                category.getRecipeType(),
                recipeTypeData,
                prepared
        );
        PersistentJeiRecipeIndexCache.record(
                fingerprint,
                "jei9",
                categoryUid.toString(),
                recipes,
                prepared
        );
        VHAccelerator.LOGGER.info(
                "Built {} JEI 9 {} recipe index plans from {} active "
                        + "recipes in {} ms",
                prepared.size(),
                categoryUid,
                recipes.size(),
                (System.nanoTime() - started) / 1_000_000L
        );
        callback.cancel();
    }

    @Unique
    private <T> boolean vhaccelerator$eligible(
            ResourceLocation categoryUid,
            RecipeTypeData<T> recipeTypeData,
            Collection<T> recipes
    ) {
        if (!JeiRecoveryReload.optimizationsAllowed()
                || !VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.VALUES
                        .persistentJeiRecipeIndexCache
                        .get()
                || !"minecraft".equals(categoryUid.getNamespace())
                || recipes.size() < VHACCELERATOR$MINIMUM_RECIPES
                || !recipeTypeData.getHiddenRecipes().isEmpty()) {
            return false;
        }
        for (T recipe : recipes) {
            if (!(recipe instanceof Recipe<?>)) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private <T> List<PersistentJeiRecipeIndexCache.ActiveRecipe<T>>
            vhaccelerator$prepare(
                    IRecipeCategory<T> category,
                    Collection<T> recipes
            ) {
        List<PersistentJeiRecipeIndexCache.ActiveRecipe<T>> prepared =
                new ArrayList<>(recipes.size());
        for (T recipe : recipes) {
            if (!category.isHandled(recipe)) {
                continue;
            }
            PersistentJeiRecipeIndexCache.ActiveRecipe<T> plan =
                    vhaccelerator$prepareRecipe(category, recipe);
            if (plan != null) {
                prepared.add(plan);
            }
        }
        return List.copyOf(prepared);
    }

    @Unique
    private <T> PersistentJeiRecipeIndexCache.ActiveRecipe<T>
            vhaccelerator$prepareRecipe(
                    IRecipeCategory<T> category,
                    T recipe
            ) {
        IIngredientSupplier supplier =
                IngredientSupplierHelper.getIngredientSupplier(
                        recipe,
                        category,
                        registeredIngredients
                );
        if (supplier == null) {
            return null;
        }
        Map<String, List<List<String>>> roles = new LinkedHashMap<>();
        boolean valid = true;
        try {
            for (RecipeIngredientRole role :
                    RecipeIngredientRole.values()) {
                List<List<String>> groups =
                        vhaccelerator$uidGroups(supplier, role);
                if (!groups.isEmpty()) {
                    roles.put(role.name(), groups);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            valid = false;
            VHAccelerator.LOGGER.debug(
                    "Skipping a JEI 9 recipe index plan that failed "
                            + "ingredient UID generation",
                    failure
            );
        }
        if (valid) {
            return PersistentJeiRecipeIndexCache.activeRecipe(
                    recipe,
                    roles
            );
        }
        return null;
    }

    @Unique
    private List<List<String>> vhaccelerator$uidGroups(
            IIngredientSupplier supplier,
            RecipeIngredientRole role
    ) {
        List<List<String>> groups = new ArrayList<>();
        supplier.getIngredientTypes(role).forEach(type -> {
            List<String> uids =
                    vhaccelerator$uids(supplier, type, role);
            if (!uids.isEmpty()) {
                groups.add(uids);
            }
        });
        return List.copyOf(groups);
    }

    @Unique
    private <V> List<String> vhaccelerator$uids(
            IIngredientSupplier supplier,
            IIngredientType<V> type,
            RecipeIngredientRole role
    ) {
        IIngredientHelper<V> helper =
                registeredIngredients.getIngredientHelper(type);
        return supplier.getIngredientStream(type, role)
                .filter(helper::isValidIngredient)
                .map(ingredient -> helper.getUniqueId(
                        ingredient,
                        UidContext.Recipe
                ))
                .distinct()
                .toList();
    }

    @Unique
    private <T> void vhaccelerator$apply(
            RecipeType<T> recipeType,
            RecipeTypeData<T> recipeTypeData,
            List<PersistentJeiRecipeIndexCache.ActiveRecipe<T>> plans
    ) {
        List<T> accepted = new ArrayList<>(plans.size());
        for (PersistentJeiRecipeIndexCache.ActiveRecipe<T> plan :
                plans) {
            for (Map.Entry<String, List<List<String>>> entry :
                    plan.roleGroups().entrySet()) {
                RecipeIngredientRole role =
                        RecipeIngredientRole.valueOf(entry.getKey());
                RecipeMap recipeMap = recipeMaps.get(role);
                ((RecipeMapIndexAccess) recipeMap)
                        .vhaccelerator$addIndexedRecipe(
                                recipeType,
                                plan.recipe(),
                                entry.getValue()
                        );
            }
            accepted.add(plan.recipe());
        }
        if (!accepted.isEmpty()) {
            recipeTypeData.addRecipes(accepted);
            recipeCategoriesVisibleCache = null;
        }
    }

    @Unique
    private <T> PersistentJeiRecipeIndexCache.ReconciledPlans<T>
            vhaccelerator$reconcile(
            IRecipeCategory<T> category,
            Collection<T> recipes,
            List<PersistentJeiRecipeIndexCache.ActiveRecipe<T>> cachedPlans
    ) {
        Map<String, PersistentJeiRecipeIndexCache.ActiveRecipe<T>> cachedById =
                new LinkedHashMap<>(cachedPlans.size() * 2);
        for (PersistentJeiRecipeIndexCache.ActiveRecipe<T> plan : cachedPlans) {
            Recipe<?> recipe = (Recipe<?>) plan.recipe();
            cachedById.put(recipe.getId().toString(), plan);
        }
        List<PersistentJeiRecipeIndexCache.ActiveRecipe<T>> reconciled =
                new ArrayList<>(recipes.size());
        int cachedCount = 0;
        int rebuiltCount = 0;
        for (T recipe : recipes) {
            if (!category.isHandled(recipe)) {
                continue;
            }
            String recipeId = ((Recipe<?>) recipe).getId().toString();
            PersistentJeiRecipeIndexCache.ActiveRecipe<T> cached =
                    cachedById.get(recipeId);
            CachedRecipeOutputReconciler.Result output = cached == null
                    ? null
                    : vhaccelerator$reconcileCachedOutput(cached);
            if (output != null && output.accepted()) {
                PersistentJeiRecipeIndexCache.ActiveRecipe<T> accepted = cached;
                if (output.rebound()) {
                    accepted = new PersistentJeiRecipeIndexCache.ActiveRecipe<>(
                            cached.recipe(),
                            output.roleGroups()
                    );
                    if (VHAcceleratorConfig.jeiRecipeAuditEnabled()) {
                        vhaccelerator$auditPlan(
                                recipeId,
                                "rebound",
                                "transient_output_uid_identity",
                                cached,
                                accepted
                        );
                    }
                }
                reconciled.add(accepted);
                cachedCount++;
                continue;
            }
            PersistentJeiRecipeIndexCache.ActiveRecipe<T> rebuilt =
                    vhaccelerator$prepareRecipe(category, recipe);
            if (rebuilt != null) {
                reconciled.add(rebuilt);
                rebuiltCount++;
            }
            if (VHAcceleratorConfig.jeiRecipeAuditEnabled()) {
                vhaccelerator$auditPlan(
                        recipeId,
                        "rebuilt",
                        cached == null
                                ? "missing_cached_plan"
                                : "output_uid_mismatch",
                        cached,
                        rebuilt
                );
            }
        }
        return new PersistentJeiRecipeIndexCache.ReconciledPlans<>(
                List.copyOf(reconciled),
                cachedCount,
                rebuiltCount
        );
    }

    @Unique
    private CachedRecipeOutputReconciler.Result
            vhaccelerator$reconcileCachedOutput(
            PersistentJeiRecipeIndexCache.ActiveRecipe<?> plan
    ) {
        Recipe<?> recipe = (Recipe<?>) plan.recipe();
        if (recipe.isSpecial()) {
            return new CachedRecipeOutputReconciler.Result(
                    CachedRecipeOutputReconciler.Outcome.EXACT,
                    plan.roleGroups()
            );
        }
        ItemStack output = recipe.getResultItem();
        if (output == null || output.isEmpty()) {
            return new CachedRecipeOutputReconciler.Result(
                    CachedRecipeOutputReconciler.Outcome.EXACT,
                    plan.roleGroups()
            );
        }
        IIngredientHelper<ItemStack> helper =
                registeredIngredients.getIngredientHelper(
                        VanillaTypes.ITEM_STACK
                );
        String liveUid = helper.getUniqueId(output, UidContext.Recipe);
        return CachedRecipeOutputReconciler.reconcile(
                plan.roleGroups(),
                RecipeIngredientRole.OUTPUT.name(),
                liveUid
        );
    }

    @Unique
    private void vhaccelerator$auditPlan(
            String recipeId,
            String action,
            String reason,
            PersistentJeiRecipeIndexCache.ActiveRecipe<?> cached,
            PersistentJeiRecipeIndexCache.ActiveRecipe<?> rebuilt
    ) {
        String comparison;
        if (cached == null) {
            comparison = "no_cached_plan";
        } else if (rebuilt == null) {
            comparison = "no_live_plan";
        } else if (cached.roleGroups().equals(rebuilt.roleGroups())) {
            comparison = "equivalent";
        } else {
            comparison = "changed";
        }
        List<String> changedRoles = new ArrayList<>();
        if (cached != null && rebuilt != null) {
            for (RecipeIngredientRole role : RecipeIngredientRole.values()) {
                String roleName = role.name();
                if (!Objects.equals(
                        cached.roleGroups().get(roleName),
                        rebuilt.roleGroups().get(roleName)
                )) {
                    changedRoles.add(roleName);
                }
            }
        }
        VHAccelerator.LOGGER.info(
                "[JEI recipe audit] JEI 9 recipe {} {} "
                        + "[reason={}, planComparison={}, changedRoles={}, "
                        + "cachedOutputs={}, liveOutputs={}]",
                recipeId,
                action,
                reason,
                comparison,
                changedRoles,
                vhaccelerator$outputGroups(cached),
                vhaccelerator$outputGroups(rebuilt)
        );
    }

    @Unique
    private List<List<String>> vhaccelerator$outputGroups(
            PersistentJeiRecipeIndexCache.ActiveRecipe<?> plan
    ) {
        if (plan == null) {
            return List.of();
        }
        return plan.roleGroups().getOrDefault(
                RecipeIngredientRole.OUTPUT.name(),
                List.of()
        );
    }
}
