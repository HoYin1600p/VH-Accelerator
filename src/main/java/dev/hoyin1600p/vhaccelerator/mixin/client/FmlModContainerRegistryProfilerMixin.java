package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.RegistryLaunchProfiler;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Attributes synchronous registry-event work to the mod container that
 * receives it. This fills the per-mod diagnostic path already exposed by
 * {@link RegistryLaunchProfiler} without changing Forge's dispatch behavior.
 */
@Mixin(value = FMLModContainer.class, remap = false)
public abstract class FmlModContainerRegistryProfilerMixin {
    @Unique
    private long vhaccelerator$registryEventStartedNanos;

    @Inject(method = "acceptEvent", at = @At("HEAD"), remap = false)
    private <T extends Event & IModBusEvent>
            void vhaccelerator$beginRegistryEvent(
                    T event,
                    CallbackInfo callback
            ) {
        if (event instanceof RegistryEvent.Register<?>) {
            vhaccelerator$registryEventStartedNanos =
                    RegistryLaunchProfiler.begin();
        }
    }

    @Inject(method = "acceptEvent", at = @At("RETURN"), remap = false)
    private <T extends Event & IModBusEvent>
            void vhaccelerator$finishRegistryEvent(
                    T event,
                    CallbackInfo callback
            ) {
        long started = vhaccelerator$registryEventStartedNanos;
        vhaccelerator$registryEventStartedNanos = 0L;
        if (!(event instanceof RegistryEvent.Register<?> registerEvent)) {
            return;
        }
        RegistryLaunchProfiler.recordEvent(
                ((FMLModContainer) (Object) this).getModId(),
                registerEvent.getName(),
                started
        );
    }
}
