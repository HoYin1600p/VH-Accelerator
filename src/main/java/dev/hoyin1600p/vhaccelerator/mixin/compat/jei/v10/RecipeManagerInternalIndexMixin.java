package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v10;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.LoginStateFingerprint;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.PersistentJeiRecipeIndexCache;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.v10.RecipeMapIndexAccess;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.common.ingredients.IIngredientSupplier;
import mezz.jei.common.ingredients.RegisteredIngredients;
import mezz.jei.common.recipes.RecipeManagerInternal;
import mezz.jei.common.recipes.collect.RecipeMap;
import mezz.jei.common.recipes.collect.RecipeTypeData;
import mezz.jei.common.util.IngredientSupplierHelper;
import net.minecraft.resources.ResourceLocation;
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
    private IIngredientVisibility ingredientVisibility;

    @Shadow
    @Final
    private EnumMap<RecipeIngredientRole, RecipeMap> recipeMaps;

    @Shadow
    private List<IRecipeCategory<?>> recipeCategoriesVisibleCache;

    @Inject(
            method = "addRecipes(Lmezz/jei/common/recipes/collect/"
                    + "RecipeTypeData;Ljava/util/Collection;)V",
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
                        "jei10",
                        categoryUid.toString(),
                        recipes
                );
        if (restored != null) {
            vhaccelerator$apply(
                    category.getRecipeType(),
                    recipeTypeData,
                    restored.recipes()
            );
            VHAccelerator.LOGGER.info(
                    "Restored {} JEI 10 {} recipe index plans in {} ms",
                    restored.recipes().size(),
                    categoryUid,
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
                    "Could not prepare the JEI 10 {} recipe index cache; "
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
                "jei10",
                categoryUid.toString(),
                recipes.size(),
                prepared
        );
        VHAccelerator.LOGGER.info(
                "Built {} JEI 10 {} recipe index plans from {} active "
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
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
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
            IIngredientSupplier supplier =
                    IngredientSupplierHelper.getIngredientSupplier(
                            recipe,
                            category,
                            registeredIngredients,
                            ingredientVisibility
                    );
            if (supplier == null) {
                continue;
            }
            Map<String, List<List<String>>> roles =
                    new LinkedHashMap<>();
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
                        "Skipping a JEI 10 recipe index plan that failed "
                                + "ingredient UID generation",
                        failure
                );
            }
            if (valid) {
                prepared.add(
                        PersistentJeiRecipeIndexCache.activeRecipe(
                                recipe,
                                roles
                        )
                );
            }
        }
        return List.copyOf(prepared);
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
}
