package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.BakeryRetention;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After the model manager has applied a bakery (bake event and block lookup
 * rebuild included), release the bakery's load-time maps that Forge would
 * otherwise keep alive through {@code ForgeModelBakery.instance}.
 */
@Mixin(ModelManager.class)
public abstract class ModelManagerBakeryReleaseMixin {
    @Inject(method = "apply", at = @At("TAIL"))
    private void vhaccelerator$releaseBakeryLoadMaps(
            ModelBakery bakery,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo callback
    ) {
        BakeryRetention.releaseAfterApply(bakery);
    }
}
