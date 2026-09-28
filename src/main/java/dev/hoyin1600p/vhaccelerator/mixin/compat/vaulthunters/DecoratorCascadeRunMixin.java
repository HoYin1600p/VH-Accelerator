package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultCascadeIndex;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.modifier.modifier.DecoratorCascadeModifier;
import iskallia.vault.core.vault.modifier.spi.ModifierContext;
import iskallia.vault.core.vault.modifier.spi.VaultModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Answers the cascade stack lookup from an index instead of scanning every
 * vault modifier entry for each stack's listener; see VaultCascadeIndex.
 * Selected only for Vault builds whose Modifiers lacks getModifierGroup.
 */
@Mixin(value = DecoratorCascadeModifier.class, remap = false)
public abstract class DecoratorCascadeRunMixin {
    @Inject(method = "getCascadeRun", at = @At("HEAD"), cancellable = true, require = 0)
    private void vhaccelerator$indexedCascadeRun(
            Vault vault,
            ModifierContext context,
            CallbackInfoReturnable<Object> callback
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.indexVaultCascadeModifiers, true)) {
            return;
        }
        Object run = VaultCascadeIndex.cascadeRun((VaultModifier<?>) (Object) this, vault, context);
        if (run != null) {
            callback.setReturnValue(run);
        }
    }
}
