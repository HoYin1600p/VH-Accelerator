package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.ParallelBlockStateJsonParser;
import dev.hoyin1600p.vhaccelerator.client.model.ModelPreparationWorkHolder;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelBakery.class, priority = 500)
public abstract class ModelBakeryBlockStateMixin {
    @Shadow
    @Final
    protected ResourceManager resourceManager;

    @Unique
    private ParallelBlockStateJsonParser.Session
            vhaccelerator$blockStateSession;

    @Inject(method = "processLoading", at = @At("HEAD"), remap = false)
    private void vhaccelerator$prepareBlockStates(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        ModelPreparationWorkHolder preparation =
                (ModelPreparationWorkHolder) this;
        vhaccelerator$blockStateSession =
                preparation.vhaccelerator$hasOverlappedPreparation()
                        ? preparation.vhaccelerator$awaitBlockStates()
                        : ParallelBlockStateJsonParser.prepare(
                                resourceManager
                        );
    }

    @Redirect(
            method = "loadModel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/"
                            + "ResourceManager;getResources("
                            + "Lnet/minecraft/resources/ResourceLocation;)"
                            + "Ljava/util/List;"
            )
    )
    private List<Resource> vhaccelerator$usePreparedBlockStates(
            ResourceManager manager,
            ResourceLocation location
    ) throws IOException {
        ParallelBlockStateJsonParser.Session session =
                vhaccelerator$blockStateSession;
        if (session != null) {
            List<Resource> prepared = session.get(location);
            if (prepared != null) {
                return prepared;
            }
        }
        return manager.getResources(location);
    }

    @Inject(method = "processLoading", at = @At("TAIL"), remap = false)
    private void vhaccelerator$releaseBlockStates(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$blockStateSession = null;
    }
}
