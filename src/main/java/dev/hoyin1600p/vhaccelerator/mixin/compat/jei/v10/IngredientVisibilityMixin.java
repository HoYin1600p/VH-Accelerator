package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v10;

import dev.hoyin1600p.vhaccelerator.client.compat.jei.EmptyJeiBlacklist;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.InitialJeiVisibilityFastPath;
import mezz.jei.common.config.IEditModeConfig;
import mezz.jei.common.ingredients.IngredientBlacklistInternal;
import mezz.jei.common.ingredients.IngredientVisibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(value = IngredientVisibility.class, remap = false)
public abstract class IngredientVisibilityMixin
        implements InitialJeiVisibilityFastPath {
    @Shadow
    @Final
    private IngredientBlacklistInternal blacklist;

    @Shadow
    @Final
    private IEditModeConfig editModeConfig;

    @Override
    public boolean vhaccelerator$hasNoHiddenIngredients() {
        return blacklist instanceof EmptyJeiBlacklist apiBlacklist
                && apiBlacklist.vhaccelerator$isEmpty()
                && editModeConfig instanceof EmptyJeiBlacklist configBlacklist
                && configBlacklist.vhaccelerator$isEmpty();
    }
}
