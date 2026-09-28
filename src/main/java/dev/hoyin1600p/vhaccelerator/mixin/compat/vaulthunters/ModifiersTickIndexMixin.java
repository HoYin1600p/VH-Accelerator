package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultModifierTicks;
import iskallia.vault.core.vault.Modifiers;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.world.storage.VirtualWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the per-tick passes over permanent modifier entries; see
 * VaultModifierTicks.
 */
@Mixin(value = Modifiers.class, remap = false)
public abstract class ModifiersTickIndexMixin implements VaultModifierTicks.Holder {
    @Unique
    private VaultModifierTicks.Index vhaccelerator$tickIndex;

    @Override
    public VaultModifierTicks.Index vhaccelerator$tickIndex() {
        return this.vhaccelerator$tickIndex;
    }

    @Override
    public void vhaccelerator$setTickIndex(VaultModifierTicks.Index index) {
        this.vhaccelerator$tickIndex = index;
    }

    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$tickIndexed(VirtualWorld world, Vault vault, CallbackInfo callback) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.indexVaultModifierTicks, true)) {
            this.vhaccelerator$tickIndex = null;
            return;
        }
        if (VaultModifierTicks.tick(this, ((Modifiers) (Object) this).getEntries())) {
            callback.cancel();
        }
    }
}
