package dev.hoyin1600p.vhaccelerator.client.model;

import javax.annotation.Nullable;
import net.minecraft.client.resources.model.ModelResourceLocation;

public interface BlockStateModelLocationHolder {
    @Nullable
    ModelResourceLocation vhaccelerator$getModelLocation();

    void vhaccelerator$setModelLocation(
            ModelResourceLocation location
    );
}
