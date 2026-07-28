package dev.hoyin1600p.vhaccelerator.client.model;

import javax.annotation.Nullable;
import net.minecraft.server.packs.resources.ResourceManager;

public interface ModelPreparationWorkHolder {
    void vhaccelerator$startModelPreparation(
            ResourceManager resourceManager
    );

    boolean vhaccelerator$hasOverlappedPreparation();

    void vhaccelerator$awaitModelLocations();

    @Nullable
    ParallelBlockStateJsonParser.Session
            vhaccelerator$awaitBlockStates();
}
