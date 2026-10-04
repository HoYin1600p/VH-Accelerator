package dev.hoyin1600p.vhaccelerator.client.model.deferred;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.IModelData;

/**
 * What a mod's model-bake-event handler receives for a deferred key: a model
 * that bakes the real one on its first use, so handlers that only wrap a model
 * no longer force its bake at launch. Every vanilla and Forge method delegates
 * to the resolved model; Forge's self-returning defaults therefore return the
 * real model.
 */
public final class DeferredBakedModelProxy implements BakedModel {
    private final Supplier<BakedModel> resolver;
    private volatile BakedModel delegate;

    public DeferredBakedModelProxy(Supplier<BakedModel> resolver) {
        this.resolver = resolver;
    }

    public BakedModel resolve() {
        BakedModel model = delegate;
        if (model == null) {
            synchronized (this) {
                model = delegate;
                if (model == null) {
                    model = resolver.get();
                    delegate = model;
                }
            }
        }
        return model;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, Random rand) {
        return resolve().getQuads(state, side, rand);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return resolve().useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return resolve().isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return resolve().usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return resolve().isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return resolve().getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return resolve().getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return resolve().getOverrides();
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            @Nonnull Random rand,
            @Nonnull IModelData extraData
    ) {
        return resolve().getQuads(state, side, rand, extraData);
    }

    @Override
    public boolean useAmbientOcclusion(BlockState state) {
        return resolve().useAmbientOcclusion(state);
    }

    @Override
    public boolean doesHandlePerspectives() {
        return resolve().doesHandlePerspectives();
    }

    @Override
    public BakedModel handlePerspective(ItemTransforms.TransformType cameraTransformType, PoseStack poseStack) {
        return resolve().handlePerspective(cameraTransformType, poseStack);
    }

    @Nonnull
    @Override
    public IModelData getModelData(
            @Nonnull BlockAndTintGetter level,
            @Nonnull BlockPos pos,
            @Nonnull BlockState state,
            @Nonnull IModelData modelData
    ) {
        return resolve().getModelData(level, pos, state, modelData);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(@Nonnull IModelData data) {
        return resolve().getParticleIcon(data);
    }

    @Override
    public boolean isLayered() {
        return resolve().isLayered();
    }

    @Override
    public List<Pair<BakedModel, RenderType>> getLayerModels(ItemStack itemStack, boolean fabulous) {
        return resolve().getLayerModels(itemStack, fabulous);
    }
}
