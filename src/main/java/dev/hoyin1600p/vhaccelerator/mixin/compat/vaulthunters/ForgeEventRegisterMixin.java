package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultEventDispatch;
import iskallia.vault.core.event.Event;
import iskallia.vault.core.event.ForgeEvent;
import java.util.function.Consumer;
import net.minecraftforge.eventbus.api.EventPriority;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vault's ForgeEvent (server tick, entity spawn and other Forge-backed events)
 * overrides {@code register} and adds each listener straight into its parent
 * event's table, never through {@code Event.register}. Without this, the
 * listener snapshot missed them: a vault opened in game registered its tick
 * listener, the snapshot kept running the old list, and the player was never
 * teleported in. Every ForgeEvent registration now marks the parent's
 * snapshot stale; see VaultEventDispatch.
 */
@SuppressWarnings("rawtypes")
@Mixin(value = ForgeEvent.class, remap = false)
public abstract class ForgeEventRegisterMixin {
    @Inject(
            method = "register(Ljava/lang/Object;Lnet/minecraftforge/eventbus/api/EventPriority;ZLjava/util/function/Consumer;I)Liskallia/vault/core/event/ForgeEvent;",
            at = @At("RETURN"),
            require = 0
    )
    private void vhaccelerator$listenerAdded(Object reference, EventPriority eventPriority, boolean receiveCancelled,
                                             Consumer listener, int priority,
                                             CallbackInfoReturnable<ForgeEvent> callback) {
        Event self = (Event) (Object) this;
        Object table = self.isChild() ? self.getParent() : self;
        if (table instanceof VaultEventDispatch.Owner owner) {
            owner.vhaccelerator$dispatchState().changed();
        }
    }
}
