package dev.hoyin1600p.vhaccelerator.mixin.compat.ctm;

import dev.hoyin1600p.vhaccelerator.client.compat.ctm.CtmModelBakeOptimizer;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.io.IOException;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.ForgeModelBakery;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
        targets = "team.chisel.ctm.client.util.TextureMetadataHandler",
        remap = false
)
public abstract class TextureMetadataHandlerMixin {
    @Shadow
    @Final
    private Object2BooleanMap<ResourceLocation> wrappedModels;

    @Shadow
    protected abstract BakedModel wrap(
            ResourceLocation location,
            UnbakedModel model,
            BakedModel bakedModel,
            ForgeModelBakery loader
    ) throws IOException;

    @Inject(method = "onModelBake", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$optimizeModelBake(
            ModelBakeEvent event,
            CallbackInfo callback
    ) {
        if (CtmModelBakeOptimizer.optimize(
                event,
                wrappedModels,
                this::wrap
        )) {
            callback.cancel();
        }
    }
}
