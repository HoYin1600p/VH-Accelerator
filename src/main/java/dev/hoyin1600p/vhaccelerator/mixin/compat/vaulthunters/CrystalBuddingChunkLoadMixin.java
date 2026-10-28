package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import iskallia.vault.block.CrystalBuddingBlock;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps crystal growth out of chunk loading and never probes an unloaded neighbour. */
@Mixin(value = CrystalBuddingBlock.class, remap = false)
public abstract class CrystalBuddingChunkLoadMixin {
    @Redirect(
            method = "checkAndScheduleTick",
            at = @At(
                    value = "INVOKE",
                    target = "Liskallia/vault/block/CrystalBuddingBlock;tick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/util/Random;)V",
                    remap = true
            ),
            require = 0
    )
    private void vhaccelerator$deferInitialGrowth(CrystalBuddingBlock block, BlockState state,
                                                 ServerLevel level, BlockPos pos, Random random) {
        if (VHAcceleratorConfig.COMMON.fixBuddingCrystalChunkLoad.get()) {
            // onLoad runs before its chunk is ready; let the normal tick scheduler handle growth.
            level.scheduleTick(pos, block, 1);
        } else {
            block.tick(state, level, pos, random);
        }
    }

    @Redirect(
            method = "getValidGrowthDirections",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
                    remap = true
            ),
            require = 0
    )
    private BlockState vhaccelerator$loadedNeighbourState(ServerLevel level, BlockPos pos) {
        if (VHAcceleratorConfig.COMMON.fixBuddingCrystalChunkLoad.get() && !level.isLoaded(pos)) {
            // Bedrock permits neither new growth nor continued growth; air would allow both a
            // direction selection and the later unguarded read in growRandom.
            return Blocks.BEDROCK.defaultBlockState();
        }
        return level.getBlockState(pos);
    }
}
