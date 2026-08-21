package dev.hoyin1600p.vhaccelerator.mixin.compat.create;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops Create's input helper from passing an unbound key to GLFW.
 *
 * <p>Create 0.5.1.i forwards the integer value of its Ponder key directly to
 * {@code InputConstants.isKeyDown}. An unbound mapping has value {@code -1},
 * which GLFW rejects and reports as error 65539 every time a relevant tooltip
 * is rendered.</p>
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.AllKeys", remap = false)
public abstract class AllKeysMixin {
    @Inject(
            method = "isKeyDown(I)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void vhaccelerator$ignoreUnboundKey(
            int key,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (key < 0
                && VHAcceleratorClientConfig.VALUES
                        .fixCreateUnboundKeyPolling.get()) {
            callback.setReturnValue(false);
        }
    }
}
