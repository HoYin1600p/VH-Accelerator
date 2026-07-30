package dev.hoyin1600p.vhaccelerator.mixin.compat.modelbake;

import dev.hoyin1600p.vhaccelerator.client.model.ModelBakeRegistryIndex;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "mekanism.client.ClientRegistration", remap = false)
public abstract class MekanismModelBakeMixin {
    @Shadow
    @Final
    private static Map<ResourceLocation, ?> customModels;

    @Redirect(
            method = "onModelBake",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;replaceAll("
                            + "Ljava/util/function/BiFunction;)V",
                    remap = false
            )
    )
    private static void vhaccelerator$replaceRegisteredModels(
            Map<ResourceLocation, BakedModel> registry,
            BiFunction<
                    ? super ResourceLocation,
                    ? super BakedModel,
                    ? extends BakedModel
                    > replacement
    ) {
        Set<String> namespaces = new LinkedHashSet<>();
        for (ResourceLocation location : customModels.keySet()) {
            namespaces.add(location.getNamespace());
        }
        ModelBakeRegistryIndex.replaceAll(
                registry,
                namespaces,
                replacement
        );
    }
}
