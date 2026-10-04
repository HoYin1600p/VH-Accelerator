package dev.hoyin1600p.vhaccelerator.mixin.client.compat.leaks;

import dev.hoyin1600p.vhaccelerator.client.SingleplayerLevelPruner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge's model-data caches are static and only reset when another level
 * asks for model data, and clearing a level fires no chunk unloads, so after
 * leaving a world they keep every loaded chunk's block-entity model data (and
 * whatever it references) until the next world. Cleared when the level is
 * cleared, as is any entity left on the shared empty stack.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftClearLevelModelDataMixin {
    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void vhaccelerator$releaseLevelModelData(Screen progressScreen, CallbackInfo callback) {
        SingleplayerLevelPruner.onClearLevel((Minecraft) (Object) this);
        ModelDataManagerAccessor.vhaccelerator$needModelDataRefresh().clear();
        ModelDataManagerAccessor.vhaccelerator$modelDataCache().clear();
        ItemStack.EMPTY.setEntityRepresentation(null);
    }
}
