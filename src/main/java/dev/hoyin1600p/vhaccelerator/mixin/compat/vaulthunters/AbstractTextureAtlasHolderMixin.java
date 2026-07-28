package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters.DeferredVaultAtlasUpload;
import dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters.DeferredVaultAtlasUploads;
import iskallia.vault.client.atlas.AbstractTextureAtlasHolder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractTextureAtlasHolder.class, remap = false)
public abstract class AbstractTextureAtlasHolderMixin
        implements DeferredVaultAtlasUpload {
    private static final int DETAIL_LIMIT = 8;

    @Shadow
    @Final
    protected TextureAtlas textureAtlas;

    @Shadow
    @Final
    protected Supplier<List<ResourceLocation>> validationSupplier;

    @Shadow
    protected abstract Stream<ResourceLocation> getResourcesToLoad();

    @Shadow
    protected abstract void validateTextures();

    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$deferInitialUpload(
            TextureAtlas.Preparations preparations,
            net.minecraft.server.packs.resources.ResourceManager resourceManager,
            net.minecraft.util.profiling.ProfilerFiller profiler,
            CallbackInfo callback
    ) {
        if (DeferredVaultAtlasUploads.defer(
                this,
                preparations,
                textureAtlas.location()
        )) {
            callback.cancel();
        }
    }

    @Override
    @Unique
    public void vhaccelerator$uploadVaultAtlas(
            TextureAtlas.Preparations preparations
    ) {
        textureAtlas.reload(preparations);
        validateTextures();
    }

    /**
     * Vault's original unused-texture check performs List.contains for every
     * stitched resource and can emit thousands of individual warnings. This
     * skips this diagnostic-only work when debug diagnostics are disabled.
     * When enabled, it keeps the same validation decisions while using
     * constant-time membership checks and bounded output.
     */
    @Inject(method = "validateTextures", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$validateTexturesInLinearTime(
            CallbackInfo callback
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.optimizeVaultAtlasValidation
                )
                || validationSupplier == null) {
            return;
        }

        if (!VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            callback.cancel();
            return;
        }

        List<ResourceLocation> expected = validationSupplier.get();
        if (expected == null) {
            return;
        }

        long started = System.nanoTime();
        Set<ResourceLocation> expectedSet = new HashSet<>(expected);
        ResourceLocation missingTexture =
                MissingTextureAtlasSprite.getLocation();
        int missingCount = 0;
        for (ResourceLocation location : expected) {
            if (!textureAtlas.getSprite(location)
                    .getName()
                    .equals(missingTexture)) {
                continue;
            }
            if (missingCount < DETAIL_LIMIT) {
                VHAccelerator.LOGGER.warn(
                        "Vault atlas is missing texture '{}'",
                        location
                );
            }
            missingCount++;
        }

        int unusedCount = 0;
        try (Stream<ResourceLocation> resources = getResourcesToLoad()) {
            java.util.Iterator<ResourceLocation> iterator =
                    resources.iterator();
            while (iterator.hasNext()) {
                ResourceLocation location = iterator.next();
                if (expectedSet.contains(location)) {
                    continue;
                }
                if (unusedCount < DETAIL_LIMIT) {
                    VHAccelerator.LOGGER.warn(
                            "Vault atlas has unused texture '{}'",
                            location
                    );
                }
                unusedCount++;
            }
        }

        if (missingCount > DETAIL_LIMIT) {
            VHAccelerator.LOGGER.warn(
                    "Vault atlas has {} additional missing textures",
                    missingCount - DETAIL_LIMIT
            );
        }
        if (unusedCount > DETAIL_LIMIT) {
            VHAccelerator.LOGGER.warn(
                    "Vault atlas has {} additional unused textures",
                    unusedCount - DETAIL_LIMIT
            );
        }
        VHAccelerator.LOGGER.info(
                "Validated {} Vault atlas expectations in {} ms "
                        + "({} missing, {} unused)",
                expected.size(),
                (System.nanoTime() - started) / 1_000_000L,
                missingCount,
                unusedCount
        );
        callback.cancel();
    }
}
