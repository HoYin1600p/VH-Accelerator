package dev.hoyin1600p.vhaccelerator.mixin.compat.decocraft;

import dev.hoyin1600p.vhaccelerator.compat.decocraft.DecocraftModelData;
import java.io.Reader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every Decocraft BBModel parse, at registration and in the client model loader, ends here. */
@Pseudo
@Mixin(
        targets = "com.razz.decocraft.models.bbmodel.BBModelLoader",
        remap = false
)
public abstract class BBModelTextureDataMixin {
    @Inject(
            method = "loadModel(Ljava/io/Reader;)Lcom/razz/decocraft/"
                    + "models/bbmodel/BBModel;",
            at = @At("RETURN")
    )
    private void vhaccelerator$clearUnusedTextureData(
            Reader reader,
            CallbackInfoReturnable<Object> callback
    ) {
        DecocraftModelData.strip(callback.getReturnValue());
    }
}
