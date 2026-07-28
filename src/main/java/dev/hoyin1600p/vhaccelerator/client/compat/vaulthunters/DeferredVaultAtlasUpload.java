package dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters;

import net.minecraft.client.renderer.texture.TextureAtlas;

public interface DeferredVaultAtlasUpload {
    void vhaccelerator$uploadVaultAtlas(
            TextureAtlas.Preparations preparations
    );
}
