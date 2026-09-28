package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultCascadeFilter;
import iskallia.vault.core.vault.modifier.modifier.DecoratorCascadeModifier;
import iskallia.vault.core.vault.modifier.spi.VaultModifier;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hands the cascade's block scan only the block entity positions whose block
 * its filter can accept; see VaultCascadeFilter. Optional: Vault builds whose
 * onGenerate differs keep their own code.
 */
@Mixin(value = DecoratorCascadeModifier.class, remap = false)
public abstract class CascadeFilterFirstMixin extends VaultModifier<DecoratorCascadeModifier.Properties> {
    private CascadeFilterFirstMixin(ResourceLocation id, DecoratorCascadeModifier.Properties properties,
                                    VaultModifier.Display display) {
        super(id, properties, display);
    }

    @Redirect(
            method = "onGenerate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getBlockEntitiesPos()Ljava/util/Set;",
                    remap = true
            ),
            require = 0
    )
    private Set<BlockPos> vhaccelerator$candidatePositions(ChunkAccess access) {
        Set<BlockPos> positions = access.getBlockEntitiesPos();
        if (this.properties == null
                || !VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.filterVaultCascadeByState, true)) {
            return positions;
        }
        return VaultCascadeFilter.candidates(this.properties.getFilter(), access, positions);
    }
}
