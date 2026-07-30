package dev.hoyin1600p.vhaccelerator.mixin.compat.ctm;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
        targets = "team.chisel.ctm.client.util.TextureMetadataHandler",
        remap = false
)
public abstract class TextureMetadataHandlerDiagnosticsMixin {
    @Inject(method = "onModelBake", at = @At("HEAD"))
    private void vhaccelerator$profileModelIdentityReuse(
            ModelBakeEvent event,
            CallbackInfo callback
    ) {
        if (!VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            return;
        }

        Map<ResourceLocation, UnbakedModel> unbakedModels =
                ObfuscationReflectionHelper.getPrivateValue(
                        ModelBakery.class,
                        event.getModelLoader(),
                        "f_119212_"
                );
        if (unbakedModels == null) {
            VHAccelerator.LOGGER.warn(
                    "CTM model-bake diagnostics could not read the "
                            + "unbaked model registry"
            );
            return;
        }

        Map<ResourceLocation, BakedModel> bakedModels =
                event.getModelRegistry();
        Set<UnbakedModel> uniqueUnbaked =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Set<BakedModel> uniqueBaked =
                Collections.newSetFromMap(new IdentityHashMap<>());
        int missingUnbaked = 0;
        int customRenderer = 0;

        for (Map.Entry<ResourceLocation, BakedModel> entry
                : bakedModels.entrySet()) {
            UnbakedModel unbaked = unbakedModels.get(entry.getKey());
            if (unbaked == null) {
                missingUnbaked++;
            } else {
                uniqueUnbaked.add(unbaked);
            }
            BakedModel baked = entry.getValue();
            if (baked != null) {
                uniqueBaked.add(baked);
                if (baked.isCustomRenderer()) {
                    customRenderer++;
                }
            }
        }

        VHAccelerator.LOGGER.info(
                "CTM model registry identity profile: {} baked keys, "
                        + "{} unique baked object(s), {} unique unbaked "
                        + "object(s), {} missing unbaked mapping(s), "
                        + "{} custom-renderer key(s)",
                bakedModels.size(),
                uniqueBaked.size(),
                uniqueUnbaked.size(),
                missingUnbaked,
                customRenderer
        );
    }
}
