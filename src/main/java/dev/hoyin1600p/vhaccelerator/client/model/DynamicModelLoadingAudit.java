package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

/**
 * Debug-only upper bound for models worth investigating for deferred loading.
 * A plain graph is not automatically safe to defer: Forge callbacks, generated
 * resources, and atlas dependencies still need independent validation.
 */
public final class DynamicModelLoadingAudit {
    private DynamicModelLoadingAudit() {
    }

    public static void report(
            Map<ResourceLocation, UnbakedModel> topLevelModels,
            Set<ResourceLocation> sequentialModels
    ) {
        if (!VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            return;
        }

        int ordinaryItems = 0;
        int reservedItems = 0;
        int ordinaryOther = 0;
        int reservedOther = 0;
        for (ResourceLocation location : topLevelModels.keySet()) {
            boolean item = location instanceof ModelResourceLocation modelLocation
                    && "inventory".equals(modelLocation.getVariant());
            boolean reserved = sequentialModels.contains(location);
            if (item) {
                if (reserved) {
                    reservedItems++;
                } else {
                    ordinaryItems++;
                }
            } else if (reserved) {
                reservedOther++;
            } else {
                ordinaryOther++;
            }
        }

        VHAccelerator.LOGGER.info(
                "Dynamic model loading audit (candidate upper bound only): "
                        + "{} ordinary inventory, {} reserved inventory, "
                        + "{} ordinary other, {} reserved other top-level models",
                ordinaryItems,
                reservedItems,
                ordinaryOther,
                reservedOther
        );
    }
}
