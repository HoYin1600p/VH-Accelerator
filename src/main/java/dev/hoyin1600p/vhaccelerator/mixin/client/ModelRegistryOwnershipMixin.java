package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.client.model.MutationTrackingMap;
import java.util.Map;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Installs ownership at allocation, before ModelManager/bakery/events share the map. */
@Mixin(ModelBakery.class)
public abstract class ModelRegistryOwnershipMixin {
    @Shadow @Final @Mutable private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

    // Forge's public constructor delegates here; the field is allocated only in this overload.
    @Redirect(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/client/color/block/BlockColors;Z)V",
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/client/resources/model/ModelBakery;bakedTopLevelModels:Ljava/util/Map;"), require = 0)
    private void vhaccelerator$ownRegistry(ModelBakery owner, Map<ResourceLocation, BakedModel> allocated) {
        bakedTopLevelModels = VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(VHAcceleratorClientConfig.VALUES.indexModelBakeRegistries)
                && allocated.getClass() == java.util.HashMap.class && allocated.isEmpty()
                ? MutationTrackingMap.ownFreshMap(allocated) : allocated;
        if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            VHAccelerator.LOGGER.info("Model registry allocation ownership: {}", bakedTopLevelModels.getClass().getName());
        }
    }
}
