package dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker;

import dev.hoyin1600p.vhaccelerator.client.compat.crafttweaker.CraftTweakerClientReplay;
import org.openzen.zenscript.parser.BracketExpressionParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = "com.blamejared.crafttweaker.impl.script.scriptrun.runner.ScriptRunner",
        remap = false
)
public abstract class ScriptRunnerMixin {
    @Inject(method = "initializeEngine", at = @At("HEAD"), remap = false)
    private void vhaccelerator$beginModuleInitialization(
            CallbackInfoReturnable<BracketExpressionParser> callback
    ) {
        CraftTweakerClientReplay.beginModuleInitialization();
    }

    @Inject(method = "initializeEngine", at = @At("RETURN"), remap = false)
    private void vhaccelerator$finishModuleInitialization(
            CallbackInfoReturnable<BracketExpressionParser> callback
    ) {
        CraftTweakerClientReplay.finishModuleInitialization();
    }

    @Inject(method = "runScripts", at = @At("HEAD"), remap = false)
    private void vhaccelerator$beginScriptRun(
            BracketExpressionParser parser,
            CallbackInfo callback
    ) {
        CraftTweakerClientReplay.beginScriptRun();
    }

    @Inject(method = "runScripts", at = @At("RETURN"), remap = false)
    private void vhaccelerator$finishScriptRun(
            BracketExpressionParser parser,
            CallbackInfo callback
    ) {
        CraftTweakerClientReplay.finishScriptRun();
    }
}
