package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.DisconnectTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftDisconnectMixin {
    /**
     * Temporary disconnect diagnostics. These phase injection points should be
     * removed after the blocking clearLevel operation has been identified.
     */
    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void vhaccelerator$beginClientTeardown(
            Screen progressScreen,
            CallbackInfo callback
    ) {
        DisconnectTimer.beginClientTeardown();
        DisconnectTimer.beginClearLevelPhases(
                ((Minecraft) (Object) this).getPendingTasksCount()
        );
    }

    @Inject(
            method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;dropAllTasks()V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void vhaccelerator$afterPendingTaskClear(
            Screen progressScreen,
            CallbackInfo callback
    ) {
        DisconnectTimer.markClearLevelPhase(
                "pending task queue clear",
                ((Minecraft) (Object) this).getPendingTasksCount()
        );
    }

    @Inject(
            method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/"
                            + "ClientPacketListener;cleanup()V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void vhaccelerator$afterConnectionCleanup(
            Screen progressScreen,
            CallbackInfo callback
    ) {
        DisconnectTimer.markClearLevelPhase("connection cleanup");
    }

    @Inject(
            method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/social/"
                            + "PlayerSocialManager;stopOnlineMode()V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void vhaccelerator$afterSocialStateReset(
            Screen progressScreen,
            CallbackInfo callback
    ) {
        DisconnectTimer.markClearLevelPhase("social state reset");
    }

    @Inject(
            method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/"
                            + "GameRenderer;resetData()V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void vhaccelerator$afterRendererReset(
            Screen progressScreen,
            CallbackInfo callback
    ) {
        DisconnectTimer.markClearLevelPhase("renderer reset");
    }

    @Inject(
            method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/ForgeHooksClient;"
                            + "firePlayerLogout("
                            + "Lnet/minecraft/client/multiplayer/"
                            + "MultiPlayerGameMode;"
                            + "Lnet/minecraft/client/player/LocalPlayer;)V",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            require = 0
    )
    private void vhaccelerator$afterForgeLogout(
            Screen progressScreen,
            CallbackInfo callback
    ) {
        DisconnectTimer.markClearLevelPhase("Forge player logout");
    }

    @Inject(
            method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;"
                            + "updateScreenAndTick("
                            + "Lnet/minecraft/client/gui/screens/Screen;)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void vhaccelerator$afterDisconnectScreenTick(
            Screen progressScreen,
            CallbackInfo callback
    ) {
        DisconnectTimer.markClearLevelPhase("disconnect screen tick");
    }

    @Inject(
            method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/eventbus/api/IEventBus;"
                            + "post(Lnet/minecraftforge/eventbus/api/Event;)Z",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            require = 0
    )
    private void vhaccelerator$afterWorldUnloadEvent(
            Screen progressScreen,
            CallbackInfo callback
    ) {
        DisconnectTimer.markClearLevelPhase("world unload listeners");
    }

    @Inject(
            method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/ForgeHooksClient;"
                            + "handleClientLevelClosing("
                            + "Lnet/minecraft/client/multiplayer/ClientLevel;)V",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            require = 0
    )
    private void vhaccelerator$afterLevelClosing(
            Screen progressScreen,
            CallbackInfo callback
    ) {
        DisconnectTimer.markClearLevelPhase(
                "server pack, GUI, and level closing"
        );
    }

    @Inject(
            method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;"
                            + "updateLevelInEngines("
                            + "Lnet/minecraft/client/multiplayer/ClientLevel;)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void vhaccelerator$afterEngineLevelDetach(
            Screen progressScreen,
            CallbackInfo callback
    ) {
        DisconnectTimer.markClearLevelPhase("engine level detach");
    }

    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("RETURN"))
    private void vhaccelerator$finishClientTeardown(
            Screen progressScreen,
            CallbackInfo callback
    ) {
        DisconnectTimer.markClearLevelPhase(
                "final references and skull cache reset"
        );
        DisconnectTimer.finishClientTeardown();
    }
}
