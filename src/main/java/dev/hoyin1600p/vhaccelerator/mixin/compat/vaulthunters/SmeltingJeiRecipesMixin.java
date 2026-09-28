package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters.VaultSmeltingIndex;
import java.util.Optional;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vault Hunters' JEI "tool smelting" category asks the recipe manager for a
 * smelting recipe once per registered item (about 39,000 lookups, each scanning
 * every smelting recipe). The lookups are answered from an index built once in
 * the recipe manager's own order; see VaultSmeltingIndex.
 */
@Mixin(targets = "iskallia.vault.item.tool.Smelting", remap = false)
public abstract class SmeltingJeiRecipesMixin {
    @Inject(method = "register(Lmezz/jei/api/registration/IRecipeRegistration;)V", at = @At("HEAD"))
    private static void vhaccelerator$beginIndex(
            mezz.jei.api.registration.IRecipeRegistration registration,
            CallbackInfo callback
    ) {
        VaultSmeltingIndex.begin();
    }

    @Inject(method = "register(Lmezz/jei/api/registration/IRecipeRegistration;)V", at = @At("RETURN"))
    private static void vhaccelerator$endIndex(
            mezz.jei.api.registration.IRecipeRegistration registration,
            CallbackInfo callback
    ) {
        VaultSmeltingIndex.end();
    }

    @Redirect(
            method = "register(Lmezz/jei/api/registration/IRecipeRegistration;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor("
                            + "Lnet/minecraft/world/item/crafting/RecipeType;"
                            + "Lnet/minecraft/world/Container;"
                            + "Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
                    remap = true
            )
    )
    private static <C extends Container, T extends Recipe<C>> Optional<T> vhaccelerator$indexedLookup(
            RecipeManager manager,
            RecipeType<T> type,
            C container,
            Level level
    ) {
        return VaultSmeltingIndex.lookup(manager, type, container, level);
    }
}
