package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.RegistryLaunchProfiler;
import java.util.Collection;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Attributes the supplier construction and registration work performed by
 * Forge DeferredRegister instances to their owning mod and registry.
 */
@Mixin(value = DeferredRegister.class, remap = false)
public abstract class DeferredRegisterProfilerMixin {
    @Unique
    private long vhaccelerator$deferredRegisterStarted;

    @Unique
    private ResourceLocation vhaccelerator$deferredRegistryName;

    @Unique
    private String vhaccelerator$deferredRegisterModId;

    @Inject(
            method = "addEntries",
            at = @At("HEAD"),
            remap = false,
            require = 1
    )
    private void vhaccelerator$beginDeferredRegistration(
            RegistryEvent.Register<?> event,
            CallbackInfo callbackInfo
    ) {
        vhaccelerator$deferredRegisterStarted = 0L;
        vhaccelerator$deferredRegistryName = null;
        vhaccelerator$deferredRegisterModId = null;
        if (!RegistryLaunchProfiler.active()) {
            return;
        }

        DeferredRegister<?> deferred =
                (DeferredRegister<?>) (Object) this;
        ResourceLocation registryName = deferred.getRegistryName();
        if (registryName == null || !registryName.equals(event.getName())) {
            return;
        }
        Collection<? extends RegistryObject<?>> entries =
                deferred.getEntries();
        if (entries.isEmpty()) {
            return;
        }

        vhaccelerator$deferredRegistryName = registryName;
        vhaccelerator$deferredRegisterModId = entries.iterator()
                .next()
                .getId()
                .getNamespace();
        vhaccelerator$deferredRegisterStarted =
                RegistryLaunchProfiler.begin();
    }

    @Inject(
            method = "addEntries",
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private void vhaccelerator$finishDeferredRegistration(
            RegistryEvent.Register<?> event,
            CallbackInfo callbackInfo
    ) {
        long started = vhaccelerator$deferredRegisterStarted;
        ResourceLocation registryName =
                vhaccelerator$deferredRegistryName;
        String modId = vhaccelerator$deferredRegisterModId;
        vhaccelerator$deferredRegisterStarted = 0L;
        vhaccelerator$deferredRegistryName = null;
        vhaccelerator$deferredRegisterModId = null;
        if (started != 0L && registryName != null && modId != null) {
            RegistryLaunchProfiler.recordEvent(
                    modId,
                    registryName,
                    started
            );
        }
    }
}
