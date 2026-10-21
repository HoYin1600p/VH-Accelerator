package dev.hoyin1600p.vhaccelerator.mixin.client;

import com.mojang.datafixers.util.Pair;
import dev.hoyin1600p.vhaccelerator.api.client.ModelBakeryEvents;
import dev.hoyin1600p.vhaccelerator.client.model.ModelPreparationWorkHolder;
import dev.hoyin1600p.vhaccelerator.client.model.parse.ParallelBlockStateJsonParser;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.texture.AtlasSet;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.Registry;
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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModelBakery.class, priority = 500)
public abstract class ModelBakeryBlockStateMixin implements ModelBakeryEvents.Access {
    @Shadow
    @Final
    protected ResourceManager resourceManager;

    @Unique
    private ModelBakeryEvents.Session vhaccelerator$integration;

    @Unique
    private ParallelBlockStateJsonParser.Session
            vhaccelerator$blockStateSession;

    @Override
    public ModelBakeryEvents.Session vhaccelerator$modelSession() {
        return vhaccelerator$integration;
    }

    @Override
    public List<Resource> vhaccelerator$resources(ResourceManager manager, ResourceLocation location) throws IOException {
        return vhaccelerator$usePreparedBlockStates(manager, location);
    }

    @Inject(method = "processLoading", at = @At("HEAD"), remap = false)
    private void vhaccelerator$prepareBlockStates(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$integration = ModelBakeryEvents.begin(resourceManager);
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
            ),
            require = 0
    )
    private List<Resource> vhaccelerator$usePreparedBlockStates(
            ResourceManager manager,
            ResourceLocation location
    ) throws IOException {
        boolean bundled = vhaccelerator$prepareNativeBlockState(location);
        ParallelBlockStateJsonParser.Session session =
                vhaccelerator$blockStateSession;
        if (session != null) {
            List<Resource> prepared = session.get(location);
            if (prepared != null) {
                return prepared;
            }
        }
        try {
            return manager.getResources(location);
        } catch (FileNotFoundException missing) {
            // Empty is safe only when a validated native definition will be merged below.
            if (bundled) {
                return List.of();
            }
            throw missing;
        }
    }

    @Unique
    private boolean vhaccelerator$prepareNativeBlockState(ResourceLocation file) {
        if (vhaccelerator$integration == null || !file.getPath().startsWith("blockstates/")
                || !file.getPath().endsWith(".json")) return false;
        ResourceLocation block = new ResourceLocation(file.getNamespace(),
                file.getPath().substring("blockstates/".length(), file.getPath().length() - ".json".length()));
        if (!Registry.BLOCK.containsKey(block)) {
            return false;
        }
        return vhaccelerator$integration.prepareBlockState(block, Registry.BLOCK.get(block).getStateDefinition());
    }

    @ModifyVariable(method = "loadModel", at = @At("STORE"), ordinal = 1)
    private List<Pair<String, BlockModelDefinition>> vhaccelerator$mergeNativeBlockState(
            List<Pair<String, BlockModelDefinition>> definitions, ResourceLocation location) {
        if (vhaccelerator$integration == null) {
            return definitions;
        }
        ResourceLocation block = new ResourceLocation(location.getNamespace(), location.getPath());
        return vhaccelerator$integration.mergeBlockState(block, definitions);
    }

    @Inject(method = "loadBlockModel", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$loadNativeModel(ResourceLocation location,
            CallbackInfoReturnable<BlockModel> callback) {
        if (vhaccelerator$integration == null) {
            return;
        }
        BlockModel model = vhaccelerator$integration.model(location);
        if (model != null) callback.setReturnValue(model);
    }

    @Inject(method = "uploadTextures", at = @At("HEAD"))
    private void vhaccelerator$beforeIntegrationBake(TextureManager textures, ProfilerFiller profiler,
            CallbackInfoReturnable<AtlasSet> callback) {
        if (vhaccelerator$integration != null) vhaccelerator$integration.beforeBaking();
    }

    @Inject(method = "uploadTextures", at = @At("RETURN"))
    private void vhaccelerator$afterIntegrationBake(TextureManager textures, ProfilerFiller profiler,
            CallbackInfoReturnable<AtlasSet> callback) {
        if (vhaccelerator$integration != null) {
            vhaccelerator$integration.afterBaking();
            vhaccelerator$integration = null;
        }
    }

    @Inject(method = "processLoading", at = @At("TAIL"), remap = false)
    private void vhaccelerator$releaseBlockStates(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$blockStateSession = null;
        if (vhaccelerator$integration != null) vhaccelerator$integration.afterPreparation();
    }
}
