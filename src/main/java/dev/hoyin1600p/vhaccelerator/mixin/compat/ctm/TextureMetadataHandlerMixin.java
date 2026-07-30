package dev.hoyin1600p.vhaccelerator.mixin.compat.ctm;

import dev.hoyin1600p.vhaccelerator.client.compat.ctm.CtmModelBakeMemoization;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.Deque;
import java.util.Map;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
        targets = "team.chisel.ctm.client.util.TextureMetadataHandler",
        remap = false
)
public abstract class TextureMetadataHandlerMixin {
    @Inject(method = "onModelBake", at = @At("HEAD"))
    private void vhaccelerator$beginIdentityMemoization(
            ModelBakeEvent event,
            CallbackInfo callback
    ) {
        CtmModelBakeMemoization.beginEvent();
    }

    @Redirect(
            method = "onModelBake",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;get("
                            + "Ljava/lang/Object;)Ljava/lang/Object;",
                    ordinal = 0,
                    remap = false
            )
    )
    private Object vhaccelerator$captureRootModel(
            Map<?, ?> models,
            Object key
    ) {
        Object model = models.get(key);
        CtmModelBakeMemoization.captureRoot(
                model instanceof UnbakedModel unbaked ? unbaked : null
        );
        return model;
    }

    @Redirect(
            method = "onModelBake",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/"
                            + "BakedModel;isCustomRenderer()Z"
            )
    )
    private boolean vhaccelerator$skipKnownPlainAlias(BakedModel model) {
        return model.isCustomRenderer()
                || CtmModelBakeMemoization.isKnownPlainRoot();
    }

    @Redirect(
            method = "onModelBake",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/"
                            + "Object2BooleanMap;getOrDefault("
                            + "Ljava/lang/Object;Z)Z",
                    remap = false
            )
    )
    private boolean vhaccelerator$reuseRootResult(
            Object2BooleanMap<ResourceLocation> results,
            Object key,
            boolean defaultValue
    ) {
        Boolean cached = CtmModelBakeMemoization.cachedResult();
        return cached != null
                ? cached
                : results.getOrDefault(key, defaultValue);
    }

    @Redirect(
            method = "onModelBake",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Deque;isEmpty()Z",
                    remap = false
            )
    )
    private boolean vhaccelerator$skipKnownPlainTraversal(
            Deque<?> dependencies
    ) {
        return CtmModelBakeMemoization.shouldSkipTraversal()
                || dependencies.isEmpty();
    }

    @Redirect(
            method = "onModelBake",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/"
                            + "Object2BooleanMap;put("
                            + "Ljava/lang/Object;Z)Z",
                    remap = false
            )
    )
    private boolean vhaccelerator$recordRootResult(
            Object2BooleanMap<ResourceLocation> results,
            Object key,
            boolean shouldWrap
    ) {
        if (!CtmModelBakeMemoization.recordResult(shouldWrap)) {
            return false;
        }
        return results.put((ResourceLocation) key, shouldWrap);
    }

    @Inject(method = "onModelBake", at = @At("RETURN"))
    private void vhaccelerator$finishIdentityMemoization(
            ModelBakeEvent event,
            CallbackInfo callback
    ) {
        CtmModelBakeMemoization.finishEvent();
    }
}
