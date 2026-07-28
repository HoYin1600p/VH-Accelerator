package dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker;

import dev.hoyin1600p.vhaccelerator.client.compat.crafttweaker.CraftTweakerClientReplay;
import org.openzen.zenscript.codemodel.SemanticModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
        targets = "com.blamejared.crafttweaker.impl.script.scriptrun.runner.ExecutingScriptRunner",
        remap = false
)
public abstract class ExecutingScriptRunnerMixin {
    @Inject(method = "executeRunAction", at = @At("HEAD"), remap = false)
    private void vhaccelerator$beginExecution(
            SemanticModule module,
            CallbackInfo callback
    ) {
        CraftTweakerClientReplay.beginExecution();
    }

    @Inject(method = "executeRunAction", at = @At("RETURN"), remap = false)
    private void vhaccelerator$finishExecution(
            SemanticModule module,
            CallbackInfo callback
    ) {
        CraftTweakerClientReplay.finishExecution();
    }
}
