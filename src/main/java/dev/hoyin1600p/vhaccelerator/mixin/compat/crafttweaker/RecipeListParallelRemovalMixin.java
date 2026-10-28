package dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker;

import com.blamejared.crafttweaker.api.recipe.RecipeList;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals.IndexedOutputRemovals;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CraftTweaker scans a whole recipe list for every removal action; Wolds'
 * scripts issue 792 removals by output against about 28,000 crafting recipes
 * each time a world opens. Predicates built by CraftTweaker's own API (output,
 * input, mod and generic matchers) are evaluated on the common pool, and the
 * matching recipes are then removed on the calling thread, as the sequential
 * loop does. A predicate defined by a script keeps the original loop.
 */
@Mixin(value = RecipeList.class, remap = false)
public abstract class RecipeListParallelRemovalMixin<T extends Recipe<?>> {
    @Shadow
    @Final
    private Map<ResourceLocation, T> recipes;

    @Shadow
    @Final
    private Map<ResourceLocation, Recipe<?>> byName;

    @Inject(method = "removeByRecipeTest", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$testInParallel(Predicate<T> recipePredicate, CallbackInfo callback) {
        if (vhaccelerator$dispatch(recipes, byName, recipePredicate,
                () -> IndexedOutputRemovals.tryRemove(recipes, byName, recipePredicate),
                () -> CompatMixinGroups.legacyCraftTweakerParallelAllowed()
                && VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .parallelCraftTweakerRecipeRemoval))) callback.cancel();
    }

    /** Both HEAD hooks offer ownership to the same dispatcher before legacy matching. */
    @Unique
    private static <K, R> boolean vhaccelerator$dispatch(Map<K, R> recipes, Map<K, ? super R> byName,
            Predicate<R> recipePredicate, BooleanSupplier indexed, BooleanSupplier legacyAllowed) {
        if (indexed.getAsBoolean()) return true;
        if (!legacyAllowed.getAsBoolean() || recipes.size() < 2_048
                || !recipePredicate.getClass().getName().startsWith("com.blamejared.crafttweaker.api.")) return false;
        List<K> matches = recipes.entrySet().parallelStream()
                .filter(entry -> recipePredicate.test(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        for (K id : matches) {
            byName.remove(id);
            recipes.remove(id);
        }
        return true;
    }
}
