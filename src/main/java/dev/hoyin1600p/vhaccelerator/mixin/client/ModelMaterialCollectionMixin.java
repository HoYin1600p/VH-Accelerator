package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredTopLevelManifest;
import dev.hoyin1600p.vhaccelerator.client.model.DeferredItemModelBaking;
import dev.hoyin1600p.vhaccelerator.client.model.DynamicModelGuard;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Avoids repeating an identical, safe material dependency walk for every
 * block state that points at the same unbaked model instance.
 *
 * <p>It is also the material sink of the warm deferred top-level stage: the
 * certified materials of every skipped inventory model join the collection
 * here, before grouping and atlas stitching. A bakery without this mixin is
 * not a {@link PersistentDeferredTopLevelManifest.MaterialSink}, so no key is
 * ever skipped there.
 */
@Mixin(ModelBakery.class)
public abstract class ModelMaterialCollectionMixin
        implements PersistentDeferredTopLevelManifest.MaterialSink {
    @Shadow
    public abstract UnbakedModel getModel(ResourceLocation location);

    @Redirect(
            method = "processLoading",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;values()Ljava/util/Collection;",
                    ordinal = 0,
                    remap = false
            )
    )
    private Collection<?> vhaccelerator$deduplicateMaterialWalks(
            Map<?, ?> topLevelModels
    ) {
        Collection<?> values =
                vhaccelerator$deduplicate(topLevelModels.values());
        UnbakedModel manifestMaterials = null;
        if ((Object) this instanceof DeferredItemModelBaking.TopLevelOwner owner) {
            DeferredItemModelBaking.TopLevelSession session =
                    owner.vhaccelerator$topLevelSession();
            if (session != null) {
                // Closes skipping even when nothing was skipped.
                manifestMaterials = session.materialNode();
            }
        }
        if (manifestMaterials == null) {
            return values;
        }
        ArrayList<Object> extended = new ArrayList<>(values.size() + 1);
        extended.addAll(values);
        extended.add(manifestMaterials);
        return extended;
    }

    @Unique
    private Collection<?> vhaccelerator$deduplicate(Collection<?> values) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .deduplicateModelMaterialCollection
                )
                || values.size() < 2) {
            return values;
        }

        DynamicModelGuard.Scanner scanner =
                DynamicModelGuard.scanner(this::getModel);
        Set<UnbakedModel> safeSeen = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );
        ArrayList<Object> reduced =
                new ArrayList<>(values.size());
        int duplicateSafeReferences = 0;
        int protectedReferences = 0;

        for (Object value : values) {
            if (!(value instanceof UnbakedModel model)
                    || scanner.requiresSequentialBaking(model)) {
                reduced.add(value);
                protectedReferences++;
                continue;
            }
            if (safeSeen.add(model)) {
                reduced.add(model);
            } else {
                duplicateSafeReferences++;
            }
        }

        reduced.trimToSize();
        VHAccelerator.LOGGER.info(
                "Reduced model material collection from {} references to {} "
                        + "[{} repeated safe references removed, {} custom "
                        + "or dynamic references retained]",
                values.size(),
                reduced.size(),
                duplicateSafeReferences,
                protectedReferences
        );
        return reduced;
    }
}
