package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.LaunchEventProfiler;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBusInvokeDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EventBus.class, remap = false)
public abstract class EventBusLaunchProfilerMixin {
    @Redirect(
            method = "post(Lnet/minecraftforge/eventbus/api/Event;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/eventbus/EventBus;post("
                            + "Lnet/minecraftforge/eventbus/api/Event;"
                            + "Lnet/minecraftforge/eventbus/api/"
                            + "IEventBusInvokeDispatcher;)Z"
            )
    )
    private boolean vhaccelerator$profileLaunchListeners(
            EventBus eventBus,
            Event event,
            IEventBusInvokeDispatcher dispatcher
    ) {
        return eventBus.post(
                event,
                (listener, currentEvent) ->
                        LaunchEventProfiler.invoke(
                                dispatcher,
                                listener,
                                currentEvent
                        )
        );
    }
}
