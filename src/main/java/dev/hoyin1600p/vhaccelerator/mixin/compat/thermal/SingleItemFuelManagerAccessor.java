package dev.hoyin1600p.vhaccelerator.mixin.compat.thermal;

import cofh.thermal.lib.util.managers.SingleItemFuelManager;
import cofh.thermal.lib.util.recipes.internal.IDynamoFuel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = SingleItemFuelManager.class, remap = false)
public interface SingleItemFuelManagerAccessor {
    @Invoker(value = "getFuel", remap = false)
    IDynamoFuel vhaccelerator$invokeGetFuel(ItemStack stack);
}
