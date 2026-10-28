package dev.hoyin1600p.vhaccelerator.mixin.compat.startup.crafttweaker;

import dev.hoyin1600p.vhaccelerator.startup.StartupWorkObserver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact-version dedicated diagnostics; target behaviour and arguments are unchanged. */
@Pseudo
@Mixin(targets = "com.blamejared.crafttweaker.impl.script.scriptrun.ScriptRunManager", remap = false)
public abstract class CraftTweakerActionObserverMixin {
    @Inject(method = "applyAction(Lcom/blamejared/crafttweaker/api/action/base/IAction;)V", at = @At("HEAD"), require = 0)
    private void vha$observeStart0(@org.spongepowered.asm.mixin.injection.Coerce Object action, CallbackInfo ci) {
        StartupWorkObserver.enter("crafttweaker.action", action);
    }

    @Inject(method = "applyAction(Lcom/blamejared/crafttweaker/api/action/base/IAction;)V", at = @At("RETURN"), require = 0)
    private void vha$observeEnd0(@org.spongepowered.asm.mixin.injection.Coerce Object action, CallbackInfo ci) {
        StartupWorkObserver.exit("crafttweaker.action");
    }

}
