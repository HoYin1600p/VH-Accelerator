package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.ParallelBlockStateJsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
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

@Mixin(ModelBakery.class)
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
        vhaccelerator$blockStateSession =
                ParallelBlockStateJsonParser.prepare(
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
        ParallelBlockStateJsonParser.clearPreparedDefinition();
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

    @Redirect(
            method = "loadModel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/model/"
                            + "BlockModelDefinition;fromStream("
                            + "Lnet/minecraft/client/renderer/block/model/"
                            + "BlockModelDefinition$Context;"
                            + "Ljava/io/Reader;)"
                            + "Lnet/minecraft/client/renderer/block/model/"
                            + "BlockModelDefinition;"
            )
    )
    private BlockModelDefinition
            vhaccelerator$usePreparedDefinition(
                    BlockModelDefinition.Context context,
                    Reader reader
            ) {
        BlockModelDefinition prepared =
                ParallelBlockStateJsonParser
                        .claimPreparedDefinition();
        return prepared != null
                ? prepared
                : BlockModelDefinition.fromStream(context, reader);
    }

    @Inject(method = "processLoading", at = @At("TAIL"), remap = false)
    private void vhaccelerator$releaseBlockStates(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$blockStateSession = null;
        ParallelBlockStateJsonParser.clearPreparedDefinition();
    }
}
