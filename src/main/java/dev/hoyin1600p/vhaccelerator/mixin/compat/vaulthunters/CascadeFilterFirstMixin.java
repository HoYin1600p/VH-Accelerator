package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultCascadeFilter;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.modifier.modifier.DecoratorCascadeModifier;
import iskallia.vault.core.vault.modifier.spi.VaultModifier;
import iskallia.vault.core.world.data.tile.TilePredicate;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hands the cascade's block scan only the block entity positions whose block
 * its filter can accept; see VaultCascadeFilter. Both hooks are optional:
 * the one matching the installed Vault build applies, others keep their code.
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

    /**
     * Vault 21.6+ moved the scan into the static cascadeChunk, which both cake
     * generation modes and Greed vaults call with the filter as an argument.
     * Older builds lack the method, so this one stays optional too.
     */
    @Redirect(
            method = "cascadeChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getBlockEntitiesPos()Ljava/util/Set;",
                    remap = true
            ),
            require = 0
    )
    private static Set<BlockPos> vhaccelerator$chunkCandidatePositions(
            ChunkAccess access, Vault vault, ServerLevelAccessor world, ChunkAccess chunk,
            String phase, TilePredicate filter, float chance, int stackCount) {
        Set<BlockPos> positions = access.getBlockEntitiesPos();
        if (filter == null
                || !VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.filterVaultCascadeByState, true)
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.filterVaultCascadeChunk, true)) {
            return positions;
        }
        return VaultCascadeFilter.candidates(filter, access, positions);
    }
}
