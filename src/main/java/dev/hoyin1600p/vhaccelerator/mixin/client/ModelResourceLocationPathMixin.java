package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.ModelLocationPaths;
import net.minecraft.client.resources.model.ModelResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every constructor delegates here; see ModelLocationPaths. */
@Mixin(ModelResourceLocation.class)
public abstract class ModelResourceLocationPathMixin {
    @Inject(method = "<init>([Ljava/lang/String;)V", at = @At("RETURN"))
    private void vhaccelerator$sharePath(String[] parts, CallbackInfo callback) {
        if (ModelLocationPaths.enabled()) {
            ModelResourceLocation self = (ModelResourceLocation) (Object) this;
            ((ResourceLocationPathAccessor) self).vhaccelerator$setPath(ModelLocationPaths.canonical(self.getPath()));
        }
    }
}
