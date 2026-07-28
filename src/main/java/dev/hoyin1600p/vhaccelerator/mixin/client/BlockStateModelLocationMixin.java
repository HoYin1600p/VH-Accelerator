package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.BlockStateModelLocationHolder;
import javax.annotation.Nullable;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateModelLocationMixin
        implements BlockStateModelLocationHolder {
    @Unique
    @Nullable
    private volatile ModelResourceLocation
            vhaccelerator$modelLocation;

    @Override
    @Nullable
    public ModelResourceLocation
            vhaccelerator$getModelLocation() {
        return vhaccelerator$modelLocation;
    }

    @Override
    public void vhaccelerator$setModelLocation(
            ModelResourceLocation location
    ) {
        vhaccelerator$modelLocation = location;
    }
}
