package dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker;

import com.blamejared.crafttweaker.api.zencode.scriptrun.IScriptRun;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;
import com.blamejared.crafttweaker.impl.script.ScriptRecipe;
import com.blamejared.crafttweaker.impl.script.scriptrun.ThroughRecipeScriptRunManager;
import dev.hoyin1600p.vhaccelerator.client.compat.crafttweaker.CraftTweakerClientReplay;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ThroughRecipeScriptRunManager.class, remap = false)
public abstract class ThroughRecipeScriptRunManagerMixin {
    @Inject(
            method = "createScriptRunFromRecipes",
            at = @At("HEAD"),
            remap = false
    )
    private static void vhaccelerator$beginSourcePreparation(
            Collection<ScriptRecipe> recipes,
            ScriptRunConfiguration configuration,
            CallbackInfoReturnable<IScriptRun> callback
    ) {
        CraftTweakerClientReplay.beginSourcePreparation();
    }

    @Inject(
            method = "createScriptRunFromRecipes",
            at = @At("RETURN"),
            remap = false
    )
    private static void vhaccelerator$finishSourcePreparation(
            Collection<ScriptRecipe> recipes,
            ScriptRunConfiguration configuration,
            CallbackInfoReturnable<IScriptRun> callback
    ) {
        CraftTweakerClientReplay.finishSourcePreparation();
    }
}
