package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.ModelBakeEventProfiler;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FMLModContainer.class, remap = false)
public abstract class FMLModContainerModelBakeProfilerMixin {
    @Unique
    private long vhaccelerator$modelBakeEventStarted = -1L;

    @Inject(method = "acceptEvent", at = @At("HEAD"))
    private void vhaccelerator$beginModelBakeEvent(
            Event event,
            CallbackInfo callback
    ) {
        vhaccelerator$modelBakeEventStarted =
                ModelBakeEventProfiler.beginContainer(event);
    }

    @Inject(method = "acceptEvent", at = @At("RETURN"))
    private void vhaccelerator$finishModelBakeEvent(
            Event event,
            CallbackInfo callback
    ) {
        ModelBakeEventProfiler.finishContainer(
                ((FMLModContainer) (Object) this).getModId(),
                vhaccelerator$modelBakeEventStarted
        );
        vhaccelerator$modelBakeEventStarted = -1L;
    }
}
