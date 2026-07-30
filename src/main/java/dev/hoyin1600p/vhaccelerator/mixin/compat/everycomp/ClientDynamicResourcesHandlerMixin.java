package dev.hoyin1600p.vhaccelerator.mixin.compat.everycomp;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables EveryCompat's optional on-disk generated-resource mirror.
 *
 * <p>The live DynamicResourcePack receives each resource before Selene checks
 * this flag. Only the diagnostic copy under debug/generated_resource_pack is
 * skipped.</p>
 */
@Pseudo
@Mixin(
        targets = "net.mehvahdjukaar.every_compat.dynamicpack."
                + "ClientDynamicResourcesHandler",
        remap = false
)
public abstract class ClientDynamicResourcesHandlerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void vhaccelerator$disableDebugResourceDump(
            CallbackInfo callback
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .disableEveryCompatDebugResourceDump,
                        true
                )) {
            return;
        }

        try {
            Method getPack = getClass().getMethod("getPack");
            Object dynamicPack = getPack.invoke(this);
            Field debugResources = dynamicPack.getClass().getField(
                    "generateDebugResources"
            );
            if (debugResources.getBoolean(dynamicPack)) {
                debugResources.setBoolean(dynamicPack, false);
                VHAccelerator.LOGGER.info(
                        "Disabled EveryCompat's generated debug resource "
                                + "mirror; live runtime resources remain "
                                + "enabled"
                );
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not disable EveryCompat's generated debug "
                            + "resource mirror; leaving its original "
                            + "behavior active",
                    failure
            );
        }
    }
}
