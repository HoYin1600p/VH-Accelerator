package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Preserves Forge's logout dispatch while identifying listeners that block
 * Minecraft's render thread during client-world teardown.
 */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class ForgeLogoutTimingMixin {
    private static final long SLOW_LISTENER_NANOS = 5_000_000L;

    @Redirect(
            method = "firePlayerLogout",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/eventbus/api/IEventBus;"
                            + "post(Lnet/minecraftforge/eventbus/api/Event;)Z"
            )
    )
    private static boolean vhaccelerator$timeLogoutListeners(
            IEventBus eventBus,
            Event event
    ) {
        if (!VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            return eventBus.post(event);
        }
        long dispatchStarted = System.nanoTime();
        boolean cancelled = eventBus.post(event, (listener, dispatchedEvent) -> {
            long listenerStarted = System.nanoTime();
            try {
                listener.invoke(dispatchedEvent);
            } finally {
                long elapsedNanos = Math.max(
                        0L,
                        System.nanoTime() - listenerStarted
                );
                if (elapsedNanos >= SLOW_LISTENER_NANOS) {
                    VHAccelerator.LOGGER.info(
                            "Forge logout listener {} completed in {} ms",
                            listener.listenerName(),
                            elapsedNanos / 1_000_000L
                    );
                }
            }
        });
        VHAccelerator.LOGGER.info(
                "Forge logout event dispatched in {} ms",
                Math.max(0L, System.nanoTime() - dispatchStarted) / 1_000_000L
        );
        return cancelled;
    }
}
