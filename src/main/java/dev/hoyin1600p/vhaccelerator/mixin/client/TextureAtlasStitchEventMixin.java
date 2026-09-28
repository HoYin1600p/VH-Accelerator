package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.AtlasStitchEvents;
import java.util.Set;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Skips a stitch event that was already fired; see AtlasStitchEvents. */
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasStitchEventMixin {
    @Redirect(
            method = "prepareToStitch",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/ForgeHooksClient;onTextureStitchedPre("
                            + "Lnet/minecraft/client/renderer/texture/TextureAtlas;Ljava/util/Set;)V",
                    remap = false
            )
    )
    private void vhaccelerator$fireUnlessPrefired(TextureAtlas atlas, Set<ResourceLocation> textures) {
        if (!AtlasStitchEvents.consume(atlas)) {
            ForgeHooksClient.onTextureStitchedPre(atlas, textures);
        }
    }
}
