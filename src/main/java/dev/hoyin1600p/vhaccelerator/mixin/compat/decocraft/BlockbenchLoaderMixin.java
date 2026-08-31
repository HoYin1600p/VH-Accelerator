package dev.hoyin1600p.vhaccelerator.mixin.compat.decocraft;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import dev.hoyin1600p.vhaccelerator.client.compat.decocraft.DecocraftBbModelCache;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = "com.razz.decocraft.models.bbmodel.BlockbenchLoader",
        remap = false
)
public abstract class BlockbenchLoaderMixin {
    @Inject(
            method = "read(Lcom/google/gson/JsonDeserializationContext;"
                    + "Lcom/google/gson/JsonObject;)Lcom/razz/decocraft/"
                    + "models/bbmodel/BlockbenchModel;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vhaccelerator$beginModelRead(
            JsonDeserializationContext context,
            JsonObject definition,
            CallbackInfoReturnable<Object> callback
    ) {
        Object reused = DecocraftBbModelCache.beginAndReuse(definition);
        if (reused != null) {
            DecocraftBbModelCache.finish();
            callback.setReturnValue(reused);
        }
    }

    @Inject(
            method = "read(Lcom/google/gson/JsonDeserializationContext;"
                    + "Lcom/google/gson/JsonObject;)Lcom/razz/decocraft/"
                    + "models/bbmodel/BlockbenchModel;",
            at = @At("RETURN")
    )
    private void vhaccelerator$finishModelRead(
            JsonDeserializationContext context,
            JsonObject definition,
            CallbackInfoReturnable<Object> callback
    ) {
        DecocraftBbModelCache.finish();
    }

    @Inject(
            method = "m_6213_(Lnet/minecraft/server/packs/resources/"
                    + "ResourceManager;)V",
            at = @At("HEAD")
    )
    private void vhaccelerator$clearForResourceReload(
            ResourceManager resourceManager,
            CallbackInfo callback
    ) {
        DecocraftBbModelCache.clear();
    }
}
