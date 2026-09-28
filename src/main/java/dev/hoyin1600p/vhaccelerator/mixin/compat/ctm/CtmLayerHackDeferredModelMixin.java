package dev.hoyin1600p.vhaccelerator.mixin.compat.ctm;

import dev.hoyin1600p.vhaccelerator.client.model.DeferredBlockStateBaking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * CTM's layer hack looks up the default-state model of every block after
 * each reload, which bakes every deferred model and loads every skipped
 * block graph. It only acts on CTM-wrapped models and on weighted or
 * multipart models with CTM parts; for anything else its check forwards to
 * the block's original render-layer predicate. A deferred, not yet baked
 * key was judged CTM-free by VHA's CTM bake pass (its whole graph has no
 * CTM texture), so answering with the missing model, for which CTM installs
 * no hack, renders identically without baking anything.
 */
@Pseudo
@Mixin(targets = "team.chisel.ctm.client.util.CTMPackReloadListener", remap = false)
public abstract class CtmLayerHackDeferredModelMixin {
    @Redirect(
            method = "refreshLayerHacks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/BlockModelShaper;m_110893_(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/resources/model/BakedModel;"
            ),
            require = 0
    )
    private BakedModel vhaccelerator$skipDeferredModels(BlockModelShaper shaper, BlockState state) {
        if (DeferredBlockStateBaking.isUnresolved(BlockModelShaper.stateToModelLocation(state))) {
            return Minecraft.getInstance().getModelManager().getMissingModel();
        }
        return shaper.getBlockModel(state);
    }
}
