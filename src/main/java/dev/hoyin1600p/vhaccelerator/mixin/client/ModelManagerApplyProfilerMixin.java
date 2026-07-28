package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.LaunchTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.AtlasSet;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.ForgeModelBakery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelManager.class)
public abstract class ModelManagerApplyProfilerMixin {
    @Unique
    private boolean vhaccelerator$profileApply;
    @Unique
    private long vhaccelerator$applyStarted;
    @Unique
    private long vhaccelerator$uploadAndBakeNanos;
    @Unique
    private long vhaccelerator$forgeModelBakeNanos;
    @Unique
    private long vhaccelerator$blockLookupNanos;

    @Inject(method = "apply", at = @At("HEAD"))
    private void vhaccelerator$beginApplyProfile(
            ModelBakery bakery,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo callback
    ) {
        vhaccelerator$profileApply =
                VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.profileClientLaunchPhases
                );
        if (!vhaccelerator$profileApply) {
            return;
        }
        vhaccelerator$applyStarted = System.nanoTime();
        vhaccelerator$uploadAndBakeNanos = 0L;
        vhaccelerator$forgeModelBakeNanos = 0L;
        vhaccelerator$blockLookupNanos = 0L;
    }

    @Redirect(
            method = "apply",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/"
                            + "ModelBakery;uploadTextures("
                            + "Lnet/minecraft/client/renderer/texture/"
                            + "TextureManager;"
                            + "Lnet/minecraft/util/profiling/"
                            + "ProfilerFiller;)"
                            + "Lnet/minecraft/client/renderer/texture/"
                            + "AtlasSet;"
            )
    )
    private AtlasSet vhaccelerator$profileUploadAndBake(
            ModelBakery bakery,
            TextureManager textureManager,
            ProfilerFiller profiler
    ) {
        if (!vhaccelerator$profileApply) {
            return bakery.uploadTextures(textureManager, profiler);
        }
        long started = System.nanoTime();
        try {
            return bakery.uploadTextures(textureManager, profiler);
        } finally {
            vhaccelerator$uploadAndBakeNanos +=
                    System.nanoTime() - started;
        }
    }

    @Redirect(
            method = "apply",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/"
                            + "ForgeHooksClient;onModelBake("
                            + "Lnet/minecraft/client/resources/model/"
                            + "ModelManager;"
                            + "Ljava/util/Map;"
                            + "Lnet/minecraftforge/client/model/"
                            + "ForgeModelBakery;)V",
                    remap = false
            )
    )
    private void vhaccelerator$profileForgeModelBake(
            ModelManager manager,
            Map<ResourceLocation, BakedModel> models,
            ForgeModelBakery bakery
    ) {
        if (!vhaccelerator$profileApply) {
            ForgeHooksClient.onModelBake(
                    manager,
                    models,
                    bakery
            );
            return;
        }
        long started = System.nanoTime();
        try {
            ForgeHooksClient.onModelBake(
                    manager,
                    models,
                    bakery
            );
        } finally {
            vhaccelerator$forgeModelBakeNanos +=
                    System.nanoTime() - started;
        }
    }

    @Redirect(
            method = "apply",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/"
                            + "BlockModelShaper;rebuildCache()V"
            )
    )
    private void vhaccelerator$profileBlockLookup(
            BlockModelShaper shaper
    ) {
        if (!vhaccelerator$profileApply) {
            shaper.rebuildCache();
            return;
        }
        long started = System.nanoTime();
        try {
            shaper.rebuildCache();
        } finally {
            vhaccelerator$blockLookupNanos +=
                    System.nanoTime() - started;
        }
    }

    @Inject(method = "apply", at = @At("TAIL"))
    private void vhaccelerator$reportApplyProfile(
            ModelBakery bakery,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo callback
    ) {
        if (!vhaccelerator$profileApply
                || vhaccelerator$applyStarted == 0L) {
            return;
        }
        long total = System.nanoTime()
                - vhaccelerator$applyStarted;
        long measured = vhaccelerator$uploadAndBakeNanos
                + vhaccelerator$forgeModelBakeNanos
                + vhaccelerator$blockLookupNanos;
        long other = Math.max(0L, total - measured);
        VHAccelerator.LOGGER.info(
                "ModelManager {} apply phases: total={} ms, "
                        + "atlas-upload-and-bake={} ms, "
                        + "Forge-model-bake-callbacks={} ms, "
                        + "block-render-lookup={} ms, other={} ms",
                LaunchTimer.isFinished()
                        ? "resource-reload"
                        : "initial",
                vhaccelerator$millis(total),
                vhaccelerator$millis(
                        vhaccelerator$uploadAndBakeNanos
                ),
                vhaccelerator$millis(
                        vhaccelerator$forgeModelBakeNanos
                ),
                vhaccelerator$millis(
                        vhaccelerator$blockLookupNanos
                ),
                vhaccelerator$millis(other)
        );
        vhaccelerator$applyStarted = 0L;
    }

    @Unique
    private static String vhaccelerator$millis(long nanos) {
        return String.format(
                java.util.Locale.ROOT,
                "%.1f",
                nanos / 1_000_000.0
        );
    }
}
