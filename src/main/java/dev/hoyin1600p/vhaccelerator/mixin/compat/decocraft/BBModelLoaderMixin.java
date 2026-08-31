package dev.hoyin1600p.vhaccelerator.mixin.compat.decocraft;

import dev.hoyin1600p.vhaccelerator.client.compat.decocraft.DecocraftBbModelCache;
import java.io.Reader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = "com.razz.decocraft.models.bbmodel.BBModelLoader",
        remap = false
)
public abstract class BBModelLoaderMixin {
    @Inject(
            method = "loadModel(Ljava/io/Reader;)Lcom/razz/decocraft/"
                    + "models/bbmodel/BBModel;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vhaccelerator$reuseParsedModel(
            Reader reader,
            CallbackInfoReturnable<Object> callback
    ) {
        Object cached = DecocraftBbModelCache.find();
        if (cached != null) {
            callback.setReturnValue(cached);
        }
    }

    @Inject(
            method = "loadModel(Ljava/io/Reader;)Lcom/razz/decocraft/"
                    + "models/bbmodel/BBModel;",
            at = @At("RETURN")
    )
    private void vhaccelerator$rememberParsedModel(
            Reader reader,
            CallbackInfoReturnable<Object> callback
    ) {
        DecocraftBbModelCache.store(callback.getReturnValue());
    }
}
