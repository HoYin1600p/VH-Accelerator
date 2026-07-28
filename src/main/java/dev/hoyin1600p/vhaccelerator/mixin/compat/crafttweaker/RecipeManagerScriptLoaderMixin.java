package dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker;

import com.blamejared.crafttweaker.impl.script.RecipeManagerScriptLoader;
import dev.hoyin1600p.vhaccelerator.client.compat.crafttweaker.CraftTweakerClientReplay;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RecipeManagerScriptLoader.class, remap = false)
public abstract class RecipeManagerScriptLoaderMixin {
    @Inject(method = "loadScriptsFromManager", at = @At("HEAD"), remap = false)
    private static void vhaccelerator$beginClientReplay(
            RecipeManager manager,
            CallbackInfo callback
    ) {
        CraftTweakerClientReplay.begin();
    }

    @Inject(
            method = "loadScriptsFromManager",
            at = @At("RETURN"),
            remap = false
    )
    private static void vhaccelerator$finishClientReplay(
            RecipeManager manager,
            CallbackInfo callback
    ) {
        CraftTweakerClientReplay.finish();
    }
}
