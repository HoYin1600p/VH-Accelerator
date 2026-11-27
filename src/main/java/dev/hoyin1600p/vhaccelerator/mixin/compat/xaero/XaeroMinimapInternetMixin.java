package dev.hoyin1600p.vhaccelerator.mixin.compat.xaero;

import dev.hoyin1600p.vhaccelerator.client.compat.xaero.XaeroUpdateScreenSuppressor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.common.misc.Internet;
import xaero.common.settings.ModSettings;

/**
 * Makes Xaero's Minimap read its "allow internet access" setting as off while
 * the update-screen suppression is on, so the online version check is skipped.
 * The player's Xaero config file is never written.
 */
@Mixin(value = Internet.class, remap = false)
public abstract class XaeroMinimapInternetMixin {
    @Redirect(method = "checkModVersion",
            at = @At(value = "FIELD",
                    target = "Lxaero/common/settings/ModSettings;allowInternetAccess:Z",
                    opcode = Opcodes.GETFIELD),
            require = 0)
    private static boolean vhaccelerator$internetOff(ModSettings settings) {
        return !XaeroUpdateScreenSuppressor.enabled() && settings.allowInternetAccess;
    }
}
