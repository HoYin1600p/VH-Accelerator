package dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker.removals;

import com.blamejared.crafttweaker.api.action.base.IAction;
import dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals.IndexedOutputRemovals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.blamejared.crafttweaker.impl.script.scriptrun.ScriptRunManager", remap = false)
public abstract class ScriptRunManagerOutputContextMixin {
    @Redirect(method = "applyActionInRun(Lcom/blamejared/crafttweaker/api/action/base/IAction;)V",
            at = @At(value = "INVOKE", target = "Lcom/blamejared/crafttweaker/api/action/base/IAction;apply()V"))
    private void vhaccelerator$outputContext(IAction action) throws Throwable {
        IndexedOutputRemovals.applyAction(action);
    }
}
