package dev.hoyin1600p.vhaccelerator.mixin.compat.placebo;

import dev.hoyin1600p.vhaccelerator.client.model.PlaceboItemMappingProfiler;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "shadows.placebo.statemap.ModelMapRegistry", remap = false)
public abstract class ModelMapRegistryProfilerMixin {
    @Inject(
            method = "getMRL(Lnet/minecraft/client/resources/model/"
                    + "ModelResourceLocation;Lnet/minecraft/resources/"
                    + "ResourceLocation;)Lnet/minecraft/client/resources/"
                    + "model/ModelResourceLocation;",
            at = @At("HEAD"),
            remap = false
    )
    private static void vhaccelerator$beginItemMapping(
            ModelResourceLocation original,
            ResourceLocation itemId,
            CallbackInfoReturnable<ModelResourceLocation> callback
    ) {
        PlaceboItemMappingProfiler.begin(original);
    }

    @Inject(
            method = "getMRL(Lnet/minecraft/client/resources/model/"
                    + "ModelResourceLocation;Lnet/minecraft/resources/"
                    + "ResourceLocation;)Lnet/minecraft/client/resources/"
                    + "model/ModelResourceLocation;",
            at = @At("RETURN"),
            remap = false
    )
    private static void vhaccelerator$finishItemMapping(
            ModelResourceLocation original,
            ResourceLocation itemId,
            CallbackInfoReturnable<ModelResourceLocation> callback
    ) {
        PlaceboItemMappingProfiler.end(callback.getReturnValue());
    }
}
