package dev.hoyin1600p.vhaccelerator.mixin.compat.thermal;

import cofh.thermal.lib.common.ThermalRecipeManagers;
import cofh.thermal.lib.util.managers.IManager;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.thermal.ParallelThermalRecipeRefresh;
import java.util.List;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ThermalRecipeManagers.class, remap = false)
public abstract class ThermalRecipeManagersMixin {
    @Shadow(remap = false)
    private RecipeManager clientRecipeManager;

    @Shadow(remap = false)
    @Final
    private List<IManager> managers;

    @Inject(
            method = "refreshClient",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void vhaccelerator$parallelClientRefresh(
            CallbackInfo callback
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.VALUES
                .parallelThermalRecipeRefresh.get()) {
            return;
        }
        if (ParallelThermalRecipeRefresh.tryRefresh(
                clientRecipeManager,
                managers
        )) {
            callback.cancel();
        }
    }
}
