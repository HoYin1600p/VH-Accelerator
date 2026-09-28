package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultEventDispatch;
import iskallia.vault.core.event.Event;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Runs Vault events from a listener snapshot; see VaultEventDispatch. Client
 * render events (package event.client) are left to the original code and to
 * VRO, which snapshots those itself.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(value = Event.class, remap = false)
public abstract class VaultEventDispatchMixin {
    @Shadow
    protected boolean child;

    @Shadow
    protected Map<Integer, Map<Object, List<Consumer>>> listeners;

    // Created on first use: Vault registers listeners during class initialization,
    // before a field initializer merged by Mixin would have run.
    @Unique
    private volatile VaultEventDispatch.State vhaccelerator$dispatch;

    @Unique
    private VaultEventDispatch.State vhaccelerator$dispatch() {
        VaultEventDispatch.State state = this.vhaccelerator$dispatch;
        if (state == null) {
            synchronized (this) {
                state = this.vhaccelerator$dispatch;
                if (state == null) {
                    state = new VaultEventDispatch.State(
                            this.getClass().getName().startsWith("iskallia.vault.core.event.client."));
                    this.vhaccelerator$dispatch = state;
                }
            }
        }
        return state;
    }

    @Inject(method = "invoke", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$invokeFromSnapshot(Object data, CallbackInfoReturnable<Object> callback) {
        if (this.child || this.listeners == null) {
            return;
        }
        VaultEventDispatch.State state = this.vhaccelerator$dispatch();
        if (state.clientEvent()
                || !VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.snapshotVaultEventListeners, true)) {
            return;
        }
        VaultEventDispatch.invoke(state, this.listeners, data);
        callback.setReturnValue(data);
    }

    @Inject(
            method = "register(Ljava/lang/Object;Ljava/util/function/Consumer;I)Liskallia/vault/core/event/Event;",
            at = @At("RETURN")
    )
    private void vhaccelerator$registered(Object reference, Consumer listener, int priority,
                                          CallbackInfoReturnable<Event> callback) {
        this.vhaccelerator$dispatch().changed();
    }

    @Inject(method = "release", at = @At("RETURN"))
    private void vhaccelerator$released(Object reference, CallbackInfoReturnable<Event> callback) {
        this.vhaccelerator$dispatch().changed();
    }
}
