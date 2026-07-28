package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.ClientConnectionProfiler;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Measures every RecipesUpdatedEvent listener while preserving Forge's
 * listener order, cancellation behavior, and exception handling.
 */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class ForgeRecipeEventTimingMixin {
    @Redirect(
            method = "onRecipesUpdated",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/eventbus/api/IEventBus;"
                            + "post(Lnet/minecraftforge/eventbus/api/Event;)Z"
            )
    )
    private static boolean vhaccelerator$timeRecipeListeners(
            IEventBus eventBus,
            Event event
    ) {
        return ClientConnectionProfiler.postTimedEvent(
                eventBus,
                event,
                "RecipesUpdatedEvent"
        );
    }
}
