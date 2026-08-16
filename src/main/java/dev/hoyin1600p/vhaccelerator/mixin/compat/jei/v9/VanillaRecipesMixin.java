package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v9;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.LoginStateFingerprint;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.AdaptiveJeiWorkScheduler;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.JeiRecoveryReload;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.PersistentRecipeValidationCache;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.VanillaRecipeValidation;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.plugins.vanilla.crafting.VanillaRecipes;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.UpgradeRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = VanillaRecipes.class, remap = false)
public abstract class VanillaRecipesMixin {
    @Unique
    private static final int VHACCELERATOR$PARALLEL_THRESHOLD = 128;

    @Shadow
    @Final
    private RecipeManager recipeManager;

    @Inject(method = "getCraftingRecipes", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$validateCrafting(
            IRecipeCategory<CraftingRecipe> category,
            CallbackInfoReturnable<Map<Boolean, List<CraftingRecipe>>> cir
    ) {
        if (!vhaccelerator$enabled()) {
            return;
        }
        List<CraftingRecipe> recipes =
                recipeManager.getAllRecipesFor(RecipeType.CRAFTING);
        LoginStateFingerprint.Snapshot fingerprint =
                vhaccelerator$fingerprint();
        long started = System.nanoTime();
        PersistentRecipeValidationCache.CraftingResult<CraftingRecipe> restored =
                fingerprint == null
                        ? null
                        : PersistentRecipeValidationCache.restoreCrafting(
                                fingerprint,
                                recipes
                        );
        if (restored != null) {
            VHAccelerator.LOGGER.info(
                    "Restored {} JEI 9 crafting validation results from "
                            + "the persistent cache in {} ms",
                    restored.handled().size() + restored.unhandled().size(),
                    (System.nanoTime() - started) / 1_000_000L
            );
            cir.setReturnValue(Map.of(
                    Boolean.TRUE,
                    restored.handled(),
                    Boolean.FALSE,
                    restored.unhandled()
            ));
            return;
        }

        boolean parallel = recipes.size() >= VHACCELERATOR$PARALLEL_THRESHOLD
                && AdaptiveJeiWorkScheduler.currentParallelism() > 1;
        if (!parallel && fingerprint == null) {
            return;
        }
        try {
            List<CraftingRecipe> inputValid = parallel
                    ? AdaptiveJeiWorkScheduler.invokeParallel(() ->
                            recipes.parallelStream()
                                    .filter(recipe ->
                                            VanillaRecipeValidation.isValid(
                                                    recipe,
                                                    9
                                            ))
                                    .toList()
                    )
                    : recipes.stream()
                            .filter(recipe ->
                                    VanillaRecipeValidation.isValid(recipe, 9))
                            .toList();
            // JEI categories may consult shared identity maps while answering.
            // Keep those lookups on the calling thread.
            Map<Boolean, List<CraftingRecipe>> validated = inputValid.stream()
                    .collect(Collectors.partitioningBy(category::isHandled));
            PersistentRecipeValidationCache.recordCrafting(
                    fingerprint,
                    recipes.size(),
                    validated.get(Boolean.TRUE),
                    validated.get(Boolean.FALSE)
            );
            if (parallel) {
                VHAccelerator.LOGGER.info(
                        "Validated {} JEI 9 crafting recipes with {} workers "
                                + "in {} ms",
                        recipes.size(),
                        AdaptiveJeiWorkScheduler.currentParallelism(),
                        (System.nanoTime() - started) / 1_000_000L
                );
            }
            cir.setReturnValue(validated);
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.warn(
                    "Parallel JEI 9 crafting validation failed; retrying sequentially",
                    failure
            );
        }
    }

    @Inject(method = "getStonecuttingRecipes", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$validateStonecutting(
            IRecipeCategory<StonecutterRecipe> category,
            CallbackInfoReturnable<List<StonecutterRecipe>> cir
    ) {
        vhaccelerator$setValidated(
                RecipeType.STONECUTTING,
                category,
                1,
                cir,
                "stonecutting"
        );
    }

    @Inject(method = "getFurnaceRecipes", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$validateFurnace(
            IRecipeCategory<SmeltingRecipe> category,
            CallbackInfoReturnable<List<SmeltingRecipe>> cir
    ) {
        vhaccelerator$setValidated(RecipeType.SMELTING, category, 1, cir, "smelting");
    }

    @Inject(method = "getSmokingRecipes", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$validateSmoking(
            IRecipeCategory<SmokingRecipe> category,
            CallbackInfoReturnable<List<SmokingRecipe>> cir
    ) {
        vhaccelerator$setValidated(RecipeType.SMOKING, category, 1, cir, "smoking");
    }

    @Inject(method = "getBlastingRecipes", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$validateBlasting(
            IRecipeCategory<BlastingRecipe> category,
            CallbackInfoReturnable<List<BlastingRecipe>> cir
    ) {
        vhaccelerator$setValidated(RecipeType.BLASTING, category, 1, cir, "blasting");
    }

    @Inject(method = "getCampfireCookingRecipes", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$validateCampfire(
            IRecipeCategory<CampfireCookingRecipe> category,
            CallbackInfoReturnable<List<CampfireCookingRecipe>> cir
    ) {
        vhaccelerator$setValidated(
                RecipeType.CAMPFIRE_COOKING,
                category,
                1,
                cir,
                "campfire"
        );
    }

    @Inject(method = "getSmithingRecipes", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$validateSmithing(
            IRecipeCategory<UpgradeRecipe> category,
            CallbackInfoReturnable<List<UpgradeRecipe>> cir
    ) {
        vhaccelerator$setValidated(RecipeType.SMITHING, category, 0, cir, "smithing");
    }

    @Unique
    private <C extends Container, T extends Recipe<C>> void vhaccelerator$setValidated(
            RecipeType<T> recipeType,
            IRecipeCategory<T> category,
            int maxInputs,
            CallbackInfoReturnable<List<T>> cir,
            String label
    ) {
        if (!vhaccelerator$enabled()) {
            return;
        }
        List<T> recipes = recipeManager.getAllRecipesFor(recipeType);
        LoginStateFingerprint.Snapshot fingerprint =
                vhaccelerator$fingerprint();
        long started = System.nanoTime();
        List<T> restored = fingerprint == null
                ? null
                : PersistentRecipeValidationCache.restore(
                        fingerprint,
                        label,
                        recipes
                );
        if (restored != null) {
            VHAccelerator.LOGGER.info(
                    "Restored {} JEI 9 {} validation results from "
                            + "the persistent cache in {} ms",
                    restored.size(),
                    label,
                    (System.nanoTime() - started) / 1_000_000L
            );
            cir.setReturnValue(restored);
            return;
        }

        boolean parallel = recipes.size() >= VHACCELERATOR$PARALLEL_THRESHOLD
                && AdaptiveJeiWorkScheduler.currentParallelism() > 1;
        if (!parallel && fingerprint == null) {
            return;
        }
        try {
            List<T> inputValid = parallel
                    ? AdaptiveJeiWorkScheduler.invokeParallel(() ->
                            recipes.parallelStream()
                                    .filter(recipe ->
                                            VanillaRecipeValidation.isValid(
                                                    recipe,
                                                    maxInputs
                                            ))
                                    .toList()
                    )
                    : recipes.stream()
                            .filter(recipe ->
                                    VanillaRecipeValidation.isValid(
                                            recipe,
                                            maxInputs
                                    ))
                            .toList();
            List<T> validated = inputValid.stream()
                    .filter(category::isHandled)
                    .toList();
            PersistentRecipeValidationCache.record(
                    fingerprint,
                    label,
                    recipes.size(),
                    validated
            );
            if (parallel) {
                VHAccelerator.LOGGER.info(
                        "Validated {} JEI 9 {} recipes with {} workers in {} ms",
                        recipes.size(),
                        label,
                        AdaptiveJeiWorkScheduler.currentParallelism(),
                        (System.nanoTime() - started) / 1_000_000L
                );
            }
            cir.setReturnValue(validated);
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.warn(
                    "Parallel JEI 9 {} validation failed; retrying sequentially",
                    label,
                    failure
            );
        }
    }

    @Unique
    private static boolean vhaccelerator$enabled() {
        return JeiRecoveryReload.optimizationsAllowed()
                && VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.VALUES
                        .parallelVanillaRecipeValidation
                        .get();
    }

    @Unique
    private static LoginStateFingerprint.Snapshot vhaccelerator$fingerprint() {
        if (!VHAcceleratorClientConfig.VALUES
                .persistentVanillaRecipeValidationCache
                .get()) {
            return null;
        }
        return LoginStateFingerprint.currentWithRecipes();
    }
}
