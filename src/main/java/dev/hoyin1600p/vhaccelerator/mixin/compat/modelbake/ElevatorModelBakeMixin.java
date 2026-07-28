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
        targets = "xyz.vsngamer.elevatorid.client.ClientRegistry",
        remap = false
)
public abstract class ElevatorModelBakeMixin {
    @Redirect(
            method = "onModelBake",
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;entrySet()Ljava/util/Set;",
                    remap = false
            )
    )
    private static Set<Map.Entry<ResourceLocation, BakedModel>>
            vhaccelerator$elevatorModels(
                    Map<ResourceLocation, BakedModel> registry
            ) {
        return ModelBakeRegistryIndex.entries(
                registry,
                "elevatorid"
        );
    }
}
