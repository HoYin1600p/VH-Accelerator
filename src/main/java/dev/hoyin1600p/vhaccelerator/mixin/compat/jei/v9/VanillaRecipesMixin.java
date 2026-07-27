package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v9;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.AdaptiveJeiWorkScheduler;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.plugins.vanilla.crafting.CategoryRecipeValidator;
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
        if (recipes.size() < VHACCELERATOR$PARALLEL_THRESHOLD) {
            return;
        }
        CategoryRecipeValidator<CraftingRecipe> validator =
                new CategoryRecipeValidator<>(category, 9);
        try {
            Map<Boolean, List<CraftingRecipe>> validated =
                    AdaptiveJeiWorkScheduler.invokeParallel(() ->
                            recipes.parallelStream()
                                    .filter(validator::isRecipeValid)
                                    .collect(Collectors.partitioningBy(
                                            validator::isRecipeHandled
                                    ))
                    );
            VHAccelerator.LOGGER.info(
                    "Validated {} JEI 9 crafting recipes with {} workers",
                    recipes.size(),
                    AdaptiveJeiWorkScheduler.currentParallelism()
            );
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
        if (recipes.size() < VHACCELERATOR$PARALLEL_THRESHOLD) {
            return;
        }
        CategoryRecipeValidator<T> validator =
                new CategoryRecipeValidator<>(category, maxInputs);
        try {
            List<T> validated = AdaptiveJeiWorkScheduler.invokeParallel(() ->
                    recipes.parallelStream()
                            .filter(recipe -> validator.isRecipeValid(recipe)
                                    && validator.isRecipeHandled(recipe))
                            .toList()
            );
            VHAccelerator.LOGGER.info(
                    "Validated {} JEI 9 {} recipes with {} workers",
                    recipes.size(),
                    label,
                    AdaptiveJeiWorkScheduler.currentParallelism()
            );
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
        return VHAcceleratorClientConfig.VALUES.enableClientOptimizations.get()
                && VHAcceleratorClientConfig.VALUES.parallelVanillaRecipeValidation.get()
                && AdaptiveJeiWorkScheduler.currentParallelism() > 1;
    }
}
