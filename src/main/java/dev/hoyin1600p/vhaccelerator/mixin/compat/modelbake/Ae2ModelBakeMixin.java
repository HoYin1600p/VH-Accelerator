package dev.hoyin1600p.vhaccelerator.mixin.compat.modelbake;

import dev.hoyin1600p.vhaccelerator.client.model.ModelBakeRegistryIndex;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(
        targets = "appeng.init.client.InitAutoRotatingModel",
        remap = false
)
public abstract class Ae2ModelBakeMixin {
    @Redirect(
            method = "onModelBake",
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;keySet()Ljava/util/Set;",
                    remap = false
            )
    )
    private static Set<ResourceLocation> vhaccelerator$ae2Models(
            Map<ResourceLocation, BakedModel> registry
    ) {
        return ModelBakeRegistryIndex.keys(registry, "ae2");
    }
}
