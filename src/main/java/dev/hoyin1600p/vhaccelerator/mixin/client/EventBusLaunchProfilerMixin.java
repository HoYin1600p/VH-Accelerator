package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.LaunchEventProfiler;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBusInvokeDispatcher;
import net.minecraftforge.eventbus.api.IEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EventBus.class, remap = false)
public abstract class EventBusLaunchProfilerMixin {
    @Redirect(
            method = "post("
                    + "Lnet/minecraftforge/eventbus/api/Event;"
                    + "Lnet/minecraftforge/eventbus/api/"
                    + "IEventBusInvokeDispatcher;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/eventbus/api/"
                            + "IEventBusInvokeDispatcher;invoke("
                            + "Lnet/minecraftforge/eventbus/api/"
                            + "IEventListener;"
                            + "Lnet/minecraftforge/eventbus/api/Event;)V"
            )
    )
    private void vhaccelerator$profileLaunchListeners(
            IEventBusInvokeDispatcher dispatcher,
            IEventListener listener,
            Event event
    ) {
        LaunchEventProfiler.invoke(
                dispatcher,
                listener,
                event
        );
    }
}
