package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.DeferredBlockStateBaking;
import dev.hoyin1600p.vhaccelerator.client.model.DeferredItemModelBaking;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops deferred bakes against the previous bakery before its atlases close. */
@Mixin(ModelManager.class)
public abstract class ModelManagerDeferredItemMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private void vhaccelerator$retireDeferredItems(
            ModelBakery bakery,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo callback
    ) {
        DeferredItemModelBaking.retireCurrent();
        DeferredBlockStateBaking.retireCurrent();
    }
}
