package dev.hoyin1600p.vhaccelerator.mixin.compat.ctm;

import dev.hoyin1600p.vhaccelerator.client.compat.ctm.CtmModelBakeMemoization;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = "team.chisel.ctm.client.util.ResourceUtil",
        remap = false
)
public abstract class ResourceUtilMixin {
    @Inject(
            method = "spriteToAbsolute",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void vhaccelerator$reuseAbsoluteTexture(
            ResourceLocation texture,
            CallbackInfoReturnable<ResourceLocation> callback
    ) {
        ResourceLocation cached =
                CtmModelBakeMemoization.cachedAbsoluteTexture(texture);
        if (cached != null) {
            callback.setReturnValue(cached);
        }
    }

    @Inject(method = "spriteToAbsolute", at = @At("RETURN"))
    private static void vhaccelerator$recordAbsoluteTexture(
            ResourceLocation texture,
            CallbackInfoReturnable<ResourceLocation> callback
    ) {
        CtmModelBakeMemoization.recordAbsoluteTexture(
                texture,
                callback.getReturnValue()
        );
    }
}
