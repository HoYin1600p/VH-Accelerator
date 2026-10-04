package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.DeferredBlockStateBaking;
import dev.hoyin1600p.vhaccelerator.client.model.deferred.DeferredItemModelBaking;
import dev.hoyin1600p.vhaccelerator.client.profiling.ModelBakeEventProfiler;
import java.util.Map;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.ForgeModelBakery;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class ForgeHooksClientModelBakeProfilerMixin {
    // Optional: ModernFix dynamic resources redirects the same calls.
    @Redirect(
            method = "onModelBake",
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/ModLoader;"
                            + "postEvent("
                            + "Lnet/minecraftforge/eventbus/api/Event;)V"
            )
    )
    private static void vhaccelerator$profileModelBakeEvent(
            ModLoader loader,
            Event event
    ) {
        boolean debug = dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig.debugDiagnosticsEnabled();
        int blocksBefore = debug ? DeferredBlockStateBaking.bakedOnDemandNow() : 0;
        int itemsBefore = debug ? DeferredItemModelBaking.bakedOnDemandNow() : 0;
        long dispatchStarted = System.nanoTime();
        int proxies;
        DeferredBlockStateBaking.beginBakeEventProxies();
        try {
            if (!ModelBakeEventProfiler.isActive()) {
                loader.postEvent((ModelBakeEvent) event);
            } else {
                long started = System.nanoTime();
                try {
                    loader.postEvent((ModelBakeEvent) event);
                } finally {
                    ModelBakeEventProfiler.recordEventDispatch(started);
                }
            }
        } finally {
            proxies = DeferredBlockStateBaking.endBakeEventProxies();
        }
        if (debug) {
            dev.hoyin1600p.vhaccelerator.VHAccelerator.LOGGER.info(
                    "[debug] Model bake event: {} ms; {} lazy stand-ins; forced {} deferred block-state and {} deferred item bakes",
                    (System.nanoTime() - dispatchStarted) / 1_000_000L,
                    proxies,
                    DeferredBlockStateBaking.bakedOnDemandNow() - blocksBefore,
                    DeferredItemModelBaking.bakedOnDemandNow() - itemsBefore
            );
        }
    }

    // Optional: ModernFix dynamic resources redirects the same calls.
    @Redirect(
            method = "onModelBake",
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/model/"
                            + "ForgeModelBakery;onPostBakeEvent("
                            + "Ljava/util/Map;)V"
            )
    )
    private static void vhaccelerator$profilePostBake(
            ForgeModelBakery bakery,
            Map<ResourceLocation, BakedModel> models
    ) {
        if (!ModelBakeEventProfiler.isActive()) {
            bakery.onPostBakeEvent(models);
            return;
        }
        long started = System.nanoTime();
        try {
            bakery.onPostBakeEvent(models);
        } finally {
            ModelBakeEventProfiler.recordPostBake(started);
        }
    }
}
