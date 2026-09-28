package dev.hoyin1600p.vhaccelerator.mixin.compat.supplementaries;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.compat.supplementaries.BookPileRepair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Loads book piles saved without books with as many plain books as their
 * block state shows; see BookPileRepair.
 */
@Pseudo
@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.block.tiles.BookPileBlockTile", remap = false)
public abstract class BookPileAtLeastOneBookMixin {
    @ModifyVariable(method = "m_142466_", at = @At("HEAD"), argsOnly = true, require = 0)
    private CompoundTag vhaccelerator$atLeastOneBook(CompoundTag tag) {
        if (!VHAcceleratorClientConfig.launchValue(
                VHAcceleratorClientConfig.VALUES.repairEmptyBookPiles, true)) {
            return tag;
        }
        return BookPileRepair.withBooks(tag, ((BlockEntity) (Object) this).getBlockState());
    }
}
