package dev.hoyin1600p.vhaccelerator.mixin.compat.decocraft;

import dev.hoyin1600p.vhaccelerator.compat.decocraft.DecocraftModelData;
import java.util.zip.ZipFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block registration's .bbmodel reads from Decocraft's own jar; see DecocraftModelData. */
@Pseudo
@Mixin(
        targets = "com.razz.decocraft.utils.JsonParser",
        remap = false
)
public abstract class JsonParserModelShareMixin {
    @Inject(
            method = "parseModel(Ljava/lang/String;Ljava/util/zip/ZipFile;)"
                    + "Lcom/razz/decocraft/models/bbmodel/BBModel;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void vhaccelerator$reuseParsedModel(
            String location,
            ZipFile zipfile,
            CallbackInfoReturnable<Object> callback
    ) {
        Object shared = DecocraftModelData.sharedRegistrationModel(location);
        if (shared != null) {
            callback.setReturnValue(shared);
        }
    }

    @Inject(
            method = "parseModel(Ljava/lang/String;Ljava/util/zip/ZipFile;)"
                    + "Lcom/razz/decocraft/models/bbmodel/BBModel;",
            at = @At("RETURN")
    )
    private static void vhaccelerator$rememberParsedModel(
            String location,
            ZipFile zipfile,
            CallbackInfoReturnable<Object> callback
    ) {
        java.util.zip.ZipEntry entry = zipfile != null ? zipfile.getEntry(location) : null;
        DecocraftModelData.rememberRegistrationModel(
                location,
                callback.getReturnValue(),
                entry != null ? entry.getCrc() : -1L
        );
    }
}
