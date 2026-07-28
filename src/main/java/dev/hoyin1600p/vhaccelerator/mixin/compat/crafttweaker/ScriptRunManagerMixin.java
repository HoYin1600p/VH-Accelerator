package dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker;

import com.blamejared.crafttweaker.api.action.base.IAction;
import com.blamejared.crafttweaker.impl.script.scriptrun.ScriptRunManager;
import dev.hoyin1600p.vhaccelerator.client.compat.crafttweaker.CraftTweakerClientReplay;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ScriptRunManager.class, remap = false)
public abstract class ScriptRunManagerMixin {
    @Inject(
            method = "makeDescription",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void vhaccelerator$skipCompactedDescription(
            IAction action,
            CallbackInfoReturnable<String> callback
    ) {
        if (CraftTweakerClientReplay.compactActionLogs()) {
            callback.setReturnValue("");
        }
    }

    @Redirect(
            method = "applyActionInRun",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/apache/logging/log4j/Logger;"
                            + "info(Ljava/lang/String;)V",
                    remap = false
            ),
            remap = false
    )
    private void vhaccelerator$compactActionLog(
            Logger logger,
            String description
    ) {
        if (CraftTweakerClientReplay.compactActionLogs()) {
            CraftTweakerClientReplay.recordCompactedActionLog();
            return;
        }
        logger.info(description);
    }
}
