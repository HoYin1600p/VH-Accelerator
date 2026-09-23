package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredTopLevelManifest;
import dev.hoyin1600p.vhaccelerator.client.model.DeferredBlockStateBaking;
import dev.hoyin1600p.vhaccelerator.client.model.DeferredBlockStateCacheMissGuard;
import dev.hoyin1600p.vhaccelerator.client.model.DeferredItemModelBaking;
import dev.hoyin1600p.vhaccelerator.client.model.DeferredItemModelOwner;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.AtlasSet;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Selects deferred inventory models for the top-level bake pass and publishes
 * the deferred registry once the eager pass finishes. The top-level bake
 * redirects exclude the selection; if neither redirect ran, nothing was
 * selected and every model was baked eagerly.
 *
 * <p>It also owns the warm top-level stage: on a validated warm launch,
 * certified inventory keys skip {@code loadTopLevel} and join the selection
 * as deferred keys whose graphs load on first use. If deferral is unavailable
 * at bake time, those graphs are loaded before the bake loop instead.
 */
@Mixin(ModelBakery.class)
public abstract class ModelBakeryDeferredItemMixin
        implements DeferredItemModelOwner, DeferredItemModelBaking.TopLevelOwner {
    @Shadow
    @Final
    @Mutable
    private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

    @Shadow
    @Final
    private Map<ResourceLocation, UnbakedModel> topLevelModels;

    @Shadow
    @Final
    @Mutable
    private Map<ResourceLocation, UnbakedModel> unbakedCache;

    @Shadow
    @Final
    @Mutable
    private Map<?, BakedModel> bakedCache;

    /** Certified block-state keys whose bake is deferred; never null once selected. */
    @Unique
    private Set<ResourceLocation> vhaccelerator$deferredBlockStates;

    /** Items plus block states; what the top-level bake loops skip. */
    @Unique
    private Set<ResourceLocation> vhaccelerator$deferredAll;

    @Shadow
    @Nullable
    public abstract BakedModel bake(ResourceLocation location, ModelState state);

    @Shadow
    @Final
    protected ResourceManager resourceManager;

    @Shadow
    public abstract UnbakedModel getModel(ResourceLocation location);

    @Unique
    private Set<ResourceLocation> vhaccelerator$deferredItems;

    @Unique
    private DeferredItemModelBaking.TopLevelSession vhaccelerator$topLevel;

    @Override
    public DeferredItemModelBaking.TopLevelSession
            vhaccelerator$topLevelSession() {
        return vhaccelerator$topLevel;
    }

    @Inject(method = "processLoading", at = @At("HEAD"), remap = false)
    private void vhaccelerator$beginTopLevelSession(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$topLevel = null;
        try {
            vhaccelerator$topLevel =
                    DeferredItemModelBaking.beginTopLevelSession(
                            resourceManager,
                            (Object) this instanceof
                                    PersistentDeferredTopLevelManifest
                                            .MaterialSink
                    );
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not start the deferred item top-level stage; "
                            + "loading every model eagerly",
                    failure
            );
        }
    }

    /** Skips only certified inventory keys on a validated warm launch. */
    @Inject(method = "loadTopLevel", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$skipCertifiedTopLevel(
            ModelResourceLocation location,
            CallbackInfo callback
    ) {
        DeferredItemModelBaking.TopLevelSession session =
                vhaccelerator$topLevel;
        if (session != null
                && session.skip(location, topLevelModels, unbakedCache)) {
            callback.cancel();
        }
    }

    @Inject(method = "processLoading", at = @At("TAIL"), remap = false)
    private void vhaccelerator$recordTopLevelManifest(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        DeferredItemModelBaking.TopLevelSession session =
                vhaccelerator$topLevel;
        if (session == null) {
            return;
        }
        try {
            session.recordIfCold(topLevelModels, unbakedCache, this::getModel);
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not certify inventory top-level models; the "
                            + "next launch will load them eagerly",
                    failure
            );
        }
    }

    /** Refuse an off-thread load only inside a deferred block-state bake. */
    @Inject(
            method = "getModel(Lnet/minecraft/resources/ResourceLocation;)"
                    + "Lnet/minecraft/client/resources/model/UnbakedModel;",
            at = @At("HEAD")
    )
    private void vhaccelerator$guardDeferredCacheMiss(
            ResourceLocation location,
            CallbackInfoReturnable<UnbakedModel> callback
    ) {
        DeferredBlockStateCacheMissGuard.check(this, unbakedCache, location);
    }

    @Override
    public Set<ResourceLocation> vhaccelerator$deferredItemModels() {
        Set<ResourceLocation> all = vhaccelerator$deferredAll;
        if (all != null) {
            return all;
        }
        Set<ResourceLocation> items = vhaccelerator$selectDeferredItems();
        Set<ResourceLocation> blocks = Collections.emptySet();
        try {
            if (DeferredBlockStateBaking.activeForThisBake()) {
                blocks = Collections.unmodifiableSet(
                        DeferredBlockStateBaking.select(
                                topLevelModels,
                                unbakedCache
                        )
                );
            }
        } catch (RuntimeException | LinkageError failure) {
            blocks = Collections.emptySet();
            VHAccelerator.LOGGER.warn(
                    "Could not select deferred block-state models; baking "
                            + "them eagerly",
                    failure
            );
        }
        vhaccelerator$deferredBlockStates = blocks;
        if (blocks.isEmpty()) {
            all = items;
        } else if (items.isEmpty()) {
            all = blocks;
        } else {
            Set<ResourceLocation> combined = new LinkedHashSet<>(items);
            combined.addAll(blocks);
            all = Collections.unmodifiableSet(combined);
        }
        vhaccelerator$deferredAll = all;
        return all;
    }

    @Unique
    private Set<ResourceLocation> vhaccelerator$selectDeferredItems() {
        Set<ResourceLocation> selected = vhaccelerator$deferredItems;
        if (selected != null) {
            return selected;
        }
        DeferredItemModelBaking.TopLevelSession session =
                vhaccelerator$topLevel;
        Set<ResourceLocation> skipped = session == null
                ? Collections.emptySet()
                : Set.copyOf(session.skipped());
        selected = Collections.emptySet();
        try {
            if ((skipped.isEmpty() || session.materialsAdded())
                    && DeferredItemModelBaking.activeForThisBake()) {
                Set<ResourceLocation> combined = new LinkedHashSet<>(
                        DeferredItemModelBaking.select(
                                topLevelModels,
                                unbakedCache
                        )
                );
                // Skipped keys stay present as deferred registry keys.
                combined.addAll(skipped);
                selected = Collections.unmodifiableSet(combined);
            }
        } catch (RuntimeException | LinkageError failure) {
            selected = Collections.emptySet();
            VHAccelerator.LOGGER.warn(
                    "Could not select deferred item models; baking all "
                            + "models eagerly",
                    failure
            );
        }
        if (selected.isEmpty() && !skipped.isEmpty()) {
            // Never leave a skipped key absent: load it for the eager bake.
            session.restoreEagerly(topLevelModels, this::getModel);
        }
        vhaccelerator$deferredItems = selected;
        return selected;
    }

    /**
     * Settles the selection before the bake loop, so skipped keys are
     * restored even when no top-level bake redirect asks for it.
     */
    @Inject(method = "uploadTextures", at = @At("HEAD"))
    private void vhaccelerator$settleDeferredItems(
            TextureManager textureManager,
            ProfilerFiller profiler,
            CallbackInfoReturnable<AtlasSet> callback
    ) {
        vhaccelerator$deferredItemModels();
    }

    @Inject(method = "uploadTextures", at = @At("RETURN"))
    private void vhaccelerator$publishDeferredItems(
            TextureManager textureManager,
            ProfilerFiller profiler,
            CallbackInfoReturnable<AtlasSet> callback
    ) {
        Set<ResourceLocation> blocks = vhaccelerator$deferredBlockStates;
        if (blocks != null && !blocks.isEmpty()) {
            // First-use bakes may run on chunk workers beside render-thread
            // bakery use; both caches must be concurrent before publishing.
            long copyStarted = System.nanoTime();
            bakedCache = DeferredBlockStateBaking.concurrentCopy(bakedCache);
            unbakedCache = DeferredBlockStateBaking.concurrentCopy(unbakedCache);
            DeferredBlockStateBaking.recordCacheCopy(System.nanoTime() - copyStarted);
            bakedTopLevelModels = DeferredBlockStateBaking.install(
                    bakedTopLevelModels,
                    blocks,
                    location -> DeferredBlockStateCacheMissGuard.bake(
                            this,
                            location,
                            key -> bake(key, BlockModelRotation.X0_Y0)
                    )
            );
        }
        Set<ResourceLocation> deferred = vhaccelerator$deferredItems;
        if (deferred == null || deferred.isEmpty()) {
            return;
        }
        DeferredItemModelBaking.TopLevelSession session =
                vhaccelerator$topLevel;
        boolean warm = session != null && !session.skipped().isEmpty();
        bakedTopLevelModels = DeferredItemModelBaking.install(
                bakedTopLevelModels,
                deferred,
                location -> warm && session.isSkipped(location)
                        ? session.loadAndBake(
                                location,
                                this::getModel,
                                unbakedCache,
                                key -> bake(key, BlockModelRotation.X0_Y0)
                        )
                        : bake(location, BlockModelRotation.X0_Y0),
                warm ? session : null
        );
    }
}
