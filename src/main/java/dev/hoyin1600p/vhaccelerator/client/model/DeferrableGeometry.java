package dev.hoyin1600p.vhaccelerator.client.model;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraftforge.fml.ModList;

/**
 * Custom model geometries whose bake is verified to be a pure, thread-safe
 * function of the loaded model and the stitched sprites, so their baking may
 * be deferred like a plain JSON model. Their graphs still load eagerly: this
 * never allows graph skipping or manifest certification.
 *
 * <p>Decocraft 3.0.4's {@code BlockbenchModel} is a Forge
 * {@code ISimpleModelGeometry} whose {@code BlockbenchBakery} holds no state;
 * the libGDX math it uses on the bake path (Matrix4 construction,
 * setToTranslation/setToScaling, Quaternion.set, Vector3 operations) touches
 * none of the library's static scratch fields. In Asgard its furniture is
 * over 5 M baked quads, nearly all of the pack's eager model geometry.</p>
 */
public final class DeferrableGeometry {
    static final String DECOCRAFT_BLOCKBENCH = "com.razz.decocraft.models.bbmodel.BlockbenchModel";
    static final String DECOCRAFT_VERIFIED_VERSION = "3.0.4-1.18.2";
    /** 0 unknown, 1 supported, -1 not. */
    private static volatile int decocraft;

    private DeferrableGeometry() {
    }

    /** Whether this custom-geometry model may have its bake deferred. */
    public static boolean allows(BlockModel model) {
        Object geometry = model.customData.getCustomGeometry();
        return geometry != null
                && DECOCRAFT_BLOCKBENCH.equals(geometry.getClass().getName())
                && decocraftVerified();
    }

    private static boolean decocraftVerified() {
        int state = decocraft;
        if (state == 0) {
            ModList mods = ModList.get();
            boolean verified = mods != null && mods.getModContainerById("decocraft")
                    .map(container -> DECOCRAFT_VERIFIED_VERSION.equals(
                            container.getModInfo().getVersion().toString()))
                    .orElse(false);
            state = verified ? 1 : -1;
            decocraft = state;
        }
        return state > 0;
    }
}
