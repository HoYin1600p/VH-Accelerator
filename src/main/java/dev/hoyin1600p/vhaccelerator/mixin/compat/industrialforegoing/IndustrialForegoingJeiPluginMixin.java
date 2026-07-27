package dev.hoyin1600p.vhaccelerator.mixin.compat.industrialforegoing;

import com.buuz135.industrial.block.resourceproduction.tile.MaterialStoneWorkFactoryTile.StoneWorkAction;
import com.buuz135.industrial.plugin.jei.JEICustomPlugin;
import com.buuz135.industrial.plugin.jei.category.StoneWorkCategory;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.industrialforegoing.IndustrialForegoingStoneWorkOptimizer;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.buuz135.industrial.plugin.jei.JEICustomPlugin", remap = false)
public abstract class IndustrialForegoingJeiPluginMixin {
    @Inject(
            method = "findAllStoneWorkOutputs",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void vhaccelerator$buildShortestStoneWorkPaths(
            ItemStack parent,
            List<StoneWorkAction> usedActions,
            CallbackInfoReturnable<List<StoneWorkCategory.Wrapper>> callback
    ) {
        if (usedActions.isEmpty()
                && VHAcceleratorClientConfig.VALUES
                        .enableClientOptimizations
                        .get()
                && VHAcceleratorClientConfig.VALUES
                        .optimizeIndustrialForegoingStoneWorkJeiRecipes
                        .get()) {
            callback.setReturnValue(
                    IndustrialForegoingStoneWorkOptimizer.findShortestOutputs(
                            (JEICustomPlugin) (Object) this,
                            parent
                    )
            );
        }
    }
}
