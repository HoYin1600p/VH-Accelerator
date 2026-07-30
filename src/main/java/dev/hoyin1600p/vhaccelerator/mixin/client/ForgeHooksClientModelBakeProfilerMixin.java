package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.ModelBakeEventProfiler;
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
    @Redirect(
            method = "onModelBake",
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
        if (!ModelBakeEventProfiler.isActive()) {
            loader.postEvent((ModelBakeEvent) event);
            return;
        }
        long started = System.nanoTime();
        try {
            loader.postEvent((ModelBakeEvent) event);
        } finally {
            ModelBakeEventProfiler.recordEventDispatch(started);
        }
    }

    @Redirect(
            method = "onModelBake",
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
