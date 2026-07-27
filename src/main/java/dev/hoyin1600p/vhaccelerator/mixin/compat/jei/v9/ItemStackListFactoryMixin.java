package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v9;

import dev.hoyin1600p.vhaccelerator.client.compat.jei.PersistentVanillaIngredientCache;
import java.util.List;
import mezz.jei.plugins.vanilla.ingredients.item.ItemStackListFactory;
import mezz.jei.util.StackHelper;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = ItemStackListFactory.class, remap = false)
public abstract class ItemStackListFactoryMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void vhaccelerator$restoreVanillaItems(
            StackHelper stackHelper,
            CallbackInfoReturnable<List<ItemStack>> cir
    ) {
        List<ItemStack> cached =
                PersistentVanillaIngredientCache.restore("9");
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "create", at = @At("RETURN"))
    private static void vhaccelerator$recordVanillaItems(
            StackHelper stackHelper,
            CallbackInfoReturnable<List<ItemStack>> cir
    ) {
        PersistentVanillaIngredientCache.record("9", cir.getReturnValue());
    }
}
