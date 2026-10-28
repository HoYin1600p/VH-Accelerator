package dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker.removals;

import dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals.CraftTweakerRecipeMaps;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.blamejared.crafttweaker.impl.helper.AccessibleElementsProvider", remap = false)
public abstract class AccessibleRecipeMapsMixin {
    @Inject(method = "recipeManager(Lnet/minecraft/world/item/crafting/RecipeManager;)V", at = @At("HEAD"))
    private void vhaccelerator$observeFreshMaps(RecipeManager manager, CallbackInfo callback) {
        CraftTweakerRecipeMaps.publishFreshCopies(manager);
    }
}
