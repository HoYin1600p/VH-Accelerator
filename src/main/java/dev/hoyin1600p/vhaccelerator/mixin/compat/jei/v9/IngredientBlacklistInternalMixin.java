package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v9;

import dev.hoyin1600p.vhaccelerator.client.compat.jei.EmptyJeiBlacklist;
import java.util.Set;
import mezz.jei.ingredients.IngredientBlacklistInternal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(value = IngredientBlacklistInternal.class, remap = false)
public abstract class IngredientBlacklistInternalMixin
        implements EmptyJeiBlacklist {
    @Shadow
    @Final
    private Set<String> ingredientBlacklist;

    @Override
    public boolean vhaccelerator$isEmpty() {
        return ingredientBlacklist.isEmpty();
    }
}
