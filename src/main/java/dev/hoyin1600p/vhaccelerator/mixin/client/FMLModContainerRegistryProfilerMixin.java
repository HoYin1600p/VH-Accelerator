package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.RegistryLaunchProfiler;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Attributes synchronous Forge registry callbacks to their owning mods while
 * the reusable launch profiler is enabled.
 */
@Mixin(value = FMLModContainer.class, remap = false)
public abstract class FMLModContainerRegistryProfilerMixin {
    @Unique
    private long vhaccelerator$registryCallbackStarted;

    @Unique
    private ResourceLocation vhaccelerator$registryCallbackName;

    @Inject(
            method = "acceptEvent",
            at = @At("HEAD"),
            remap = false,
            require = 1
    )
    private void vhaccelerator$beginRegistryCallback(
            Event event,
            CallbackInfo callbackInfo
    ) {
        vhaccelerator$registryCallbackStarted = 0L;
        vhaccelerator$registryCallbackName = null;
        if (!(event instanceof RegistryEvent.Register<?> registerEvent)
                || !RegistryLaunchProfiler.active()) {
            return;
        }
        vhaccelerator$registryCallbackName = registerEvent.getName();
        vhaccelerator$registryCallbackStarted = RegistryLaunchProfiler.begin();
    }

    @Inject(
            method = "acceptEvent",
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private void vhaccelerator$finishRegistryCallback(
            Event event,
            CallbackInfo callbackInfo
    ) {
        long started = vhaccelerator$registryCallbackStarted;
        ResourceLocation registryName = vhaccelerator$registryCallbackName;
        vhaccelerator$registryCallbackStarted = 0L;
        vhaccelerator$registryCallbackName = null;
        if (started != 0L && registryName != null) {
            RegistryLaunchProfiler.recordEvent(
                    ((FMLModContainer) (Object) this).getModId(),
                    registryName,
                    started
            );
        }
    }
}
