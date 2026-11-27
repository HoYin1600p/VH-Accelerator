package dev.hoyin1600p.vhaccelerator.mixin.compat.xaero;

import dev.hoyin1600p.vhaccelerator.client.compat.xaero.XaeroUpdateScreenSuppressor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.patreon.Patreon;
import xaero.map.settings.ModSettings;

/**
 * Stops Xaero's World Map opening its full-screen update notice on the title
 * screen, and treats Xaero's "allow internet access" as off so the online
 * version and Patreon checks are skipped. The Xaero config file is not written.
 */
@Mixin(value = Patreon.class, remap = false)
public abstract class XaeroWorldMapUpdateScreenMixin {
    @Inject(method = "needsNotification", at = @At("HEAD"), cancellable = true, require = 0)
    private static void vhaccelerator$suppressUpdateScreen(CallbackInfoReturnable<Boolean> cir) {
        if (XaeroUpdateScreenSuppressor.enabled()) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "checkPatreon",
            at = @At(value = "FIELD",
                    target = "Lxaero/map/settings/ModSettings;allowInternetAccess:Z",
                    opcode = Opcodes.GETFIELD),
            require = 0)
    private static boolean vhaccelerator$internetOff(ModSettings settings) {
        return !XaeroUpdateScreenSuppressor.enabled() && settings.allowInternetAccess;
    }
}
