package dev.hoyin1600p.vhaccelerator.mixin.compat.startup.kubejs;

import dev.hoyin1600p.vhaccelerator.startup.StartupWorkObserver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact-version dedicated diagnostics; target behaviour and arguments are unchanged. */
@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.script.ScriptManager", remap = false)
public abstract class KubeScriptObserverMixin {
    @Inject(method = "loadFromDirectory()V", at = @At("HEAD"), require = 0)
    private void vha$observeStart0(CallbackInfo ci) {
        StartupWorkObserver.enter("kubejs.script.discover", this);
    }

    @Inject(method = "loadFromDirectory()V", at = @At("RETURN"), require = 0)
    private void vha$observeEnd0(CallbackInfo ci) {
        StartupWorkObserver.exit("kubejs.script.discover");
    }

    @Inject(method = "load()V", at = @At("HEAD"), require = 0)
    private void vha$observeStart1(CallbackInfo ci) {
        StartupWorkObserver.enter("kubejs.script.load", this);
    }

    @Inject(method = "load()V", at = @At("RETURN"), require = 0)
    private void vha$observeEnd1(CallbackInfo ci) {
        StartupWorkObserver.exit("kubejs.script.load");
    }

}
