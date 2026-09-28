package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultModifierTicks;
import iskallia.vault.core.data.IDataObject;
import iskallia.vault.core.data.key.GenericFieldKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells VaultModifierTicks when a modifier entry or context it treats as
 * permanent is written to. Unwatched objects only pay a field check.
 */
@SuppressWarnings("rawtypes")
@Mixin(value = IDataObject.class, remap = false)
public abstract class VaultDataWatchMixin implements VaultModifierTicks.Watchable {
    @Unique
    private boolean vhaccelerator$watched;

    @Override
    public boolean vhaccelerator$isWatched() {
        return this.vhaccelerator$watched;
    }

    @Override
    public void vhaccelerator$setWatched(boolean watched) {
        this.vhaccelerator$watched = watched;
    }

    @Inject(
            method = "set(Liskallia/vault/core/data/key/GenericFieldKey;Ljava/lang/Object;)Liskallia/vault/core/data/IDataObject;",
            at = @At("HEAD")
    )
    private void vhaccelerator$beforeSet(GenericFieldKey key, Object value, CallbackInfoReturnable<IDataObject> callback) {
        if (this.vhaccelerator$watched) {
            VaultModifierTicks.watchedWrite();
        }
    }

    @Inject(
            method = "remove(Liskallia/vault/core/data/key/GenericFieldKey;)Liskallia/vault/core/data/IDataObject;",
            at = @At("HEAD")
    )
    private void vhaccelerator$beforeRemove(GenericFieldKey key, CallbackInfoReturnable<IDataObject> callback) {
        if (this.vhaccelerator$watched) {
            VaultModifierTicks.watchedWrite();
        }
    }
}
