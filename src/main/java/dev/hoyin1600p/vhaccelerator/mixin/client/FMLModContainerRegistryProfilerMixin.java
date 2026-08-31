package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.RegistryLaunchProfiler;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Attributes synchronous Forge registry callbacks to their owning mods while
 * the reusable launch profiler is enabled.
 */
@Mixin(value = FMLModContainer.class, remap = false)
public abstract class FMLModContainerRegistryProfilerMixin {
    @Redirect(
            method = "acceptEvent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/eventbus/api/IEventBus;"
                            + "post(Lnet/minecraftforge/eventbus/api/Event;)Z"
            ),
            remap = false
    )
    private <T extends Event & IModBusEvent> boolean
            vhaccelerator$profileRegistryCallback(
                    IEventBus eventBus,
                    Event event
            ) {
        if (!(event instanceof RegistryEvent.Register<?> registerEvent)
                || !RegistryLaunchProfiler.active()) {
            return eventBus.post(event);
        }

        long started = RegistryLaunchProfiler.begin();
        try {
            return eventBus.post(event);
        } finally {
            RegistryLaunchProfiler.recordEvent(
                    ((FMLModContainer) (Object) this).getModId(),
                    registerEvent.getName(),
                    started
            );
        }
    }
}
