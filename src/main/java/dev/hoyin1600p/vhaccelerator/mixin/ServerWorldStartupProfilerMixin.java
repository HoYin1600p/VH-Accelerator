package dev.hoyin1600p.vhaccelerator.mixin;

import dev.hoyin1600p.vhaccelerator.startup.ServerLaunchTimer;
import dev.hoyin1600p.vhaccelerator.startup.ServerStartupReport;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class ServerWorldStartupProfilerMixin {
    @Unique private ServerStartupReport.Span vhaccelerator$levelLoad;
    @Unique private ServerStartupReport.Span vhaccelerator$levelConstruction;
    @Unique private ServerStartupReport.Span vhaccelerator$spawn;

    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void vhaccelerator$loadStart(CallbackInfo callback) {
        vhaccelerator$levelLoad = ServerLaunchTimer.beginSpan("level.load", null, null, null);
    }

    @Inject(method = "loadLevel", at = @At("RETURN"))
    private void vhaccelerator$loadEnd(CallbackInfo callback) {
        ServerLaunchTimer.endSpan(vhaccelerator$levelLoad, null);
        vhaccelerator$levelLoad = null;
    }

    @Inject(method = "createLevels", at = @At("HEAD"))
    private void vhaccelerator$levelsStart(CallbackInfo callback) {
        ServerLaunchTimer.checkpoint("level_construction", "hook:MinecraftServer.createLevels HEAD");
        vhaccelerator$levelConstruction = ServerLaunchTimer.beginSpan("level.construction",
                vhaccelerator$levelLoad == null ? null : vhaccelerator$levelLoad.id(), null, null);
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void vhaccelerator$levelsEnd(CallbackInfo callback) {
        ServerLaunchTimer.endSpan(vhaccelerator$levelConstruction, null);
        vhaccelerator$levelConstruction = null;
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"))
    private void vhaccelerator$spawnStart(CallbackInfo callback) {
        ServerLaunchTimer.checkpoint("spawn_preparation", "hook:MinecraftServer.prepareLevels HEAD");
        vhaccelerator$spawn = ServerLaunchTimer.beginSpan("spawn.preparation",
                vhaccelerator$levelLoad == null ? null : vhaccelerator$levelLoad.id(), null, null);
    }

    @Inject(method = "prepareLevels", at = @At("RETURN"))
    private void vhaccelerator$spawnEnd(CallbackInfo callback) {
        ServerLaunchTimer.endSpan(vhaccelerator$spawn, null);
        vhaccelerator$spawn = null;
    }
}
