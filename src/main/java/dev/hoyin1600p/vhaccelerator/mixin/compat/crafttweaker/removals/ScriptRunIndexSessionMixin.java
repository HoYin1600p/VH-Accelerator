package dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker.removals;

import com.blamejared.crafttweaker.impl.script.scriptrun.runner.IScriptRunner;
import dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals.OutputIndexGenerations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.blamejared.crafttweaker.impl.script.scriptrun.ScriptRun", remap = false)
public abstract class ScriptRunIndexSessionMixin {
    @Redirect(method = "executeRun()V", at = @At(value = "INVOKE",
            target = "Lcom/blamejared/crafttweaker/impl/script/scriptrun/runner/IScriptRunner;run()V"))
    private void vhaccelerator$generation(IScriptRunner runner) throws Exception {
        OutputIndexGenerations.run(runner::run);
    }
}
