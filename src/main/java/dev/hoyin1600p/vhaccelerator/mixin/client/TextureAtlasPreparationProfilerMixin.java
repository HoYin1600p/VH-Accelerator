package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TextureAtlas.class, priority = 2_000)
public abstract class TextureAtlasPreparationProfilerMixin {
    @Shadow
    @Final
    private ResourceLocation location;

    @Unique
    private boolean vhaccelerator$profilePreparation;
    @Unique
    private long vhaccelerator$stageStarted;
    @Unique
    private String vhaccelerator$currentStage;
    @Unique
    private Map<String, Long> vhaccelerator$stageNanos;

    @Inject(method = "prepareToStitch", at = @At("HEAD"))
    private void vhaccelerator$beginAtlasProfile(
            ResourceManager resourceManager,
            Stream<ResourceLocation> materials,
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfoReturnable<TextureAtlas.Preparations> callback
    ) {
        vhaccelerator$profilePreparation =
                VHAcceleratorClientConfig.launchProfilingEnabled();
        if (!vhaccelerator$profilePreparation) {
            return;
        }
        vhaccelerator$stageNanos = new LinkedHashMap<>();
        vhaccelerator$currentStage = "material-set";
        vhaccelerator$stageStarted = System.nanoTime();
    }

    @Inject(
            method = "prepareToStitch",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;"
                            + "popPush(Ljava/lang/String;)V",
                    ordinal = 0
            ),
            require = 0
    )
    private void vhaccelerator$profileMetadata(
            ResourceManager resourceManager,
            Stream<ResourceLocation> materials,
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfoReturnable<TextureAtlas.Preparations> callback
    ) {
        vhaccelerator$transitionTo("metadata-and-pre-stitch-hooks");
    }

    @Inject(
            method = "prepareToStitch",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;"
                            + "popPush(Ljava/lang/String;)V",
                    ordinal = 1
            ),
            require = 0
    )
    private void vhaccelerator$profileRegistration(
            ResourceManager resourceManager,
            Stream<ResourceLocation> materials,
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfoReturnable<TextureAtlas.Preparations> callback
    ) {
        vhaccelerator$transitionTo("missing-sprite-registration");
    }

    @Inject(
            method = "prepareToStitch",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;"
                            + "popPush(Ljava/lang/String;)V",
                    ordinal = 2
            ),
            require = 0
    )
    private void vhaccelerator$profilePacking(
            ResourceManager resourceManager,
            Stream<ResourceLocation> materials,
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfoReturnable<TextureAtlas.Preparations> callback
    ) {
        vhaccelerator$transitionTo("packing");
    }

    @Inject(
            method = "prepareToStitch",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;"
                            + "popPush(Ljava/lang/String;)V",
                    ordinal = 3
            ),
            require = 0
    )
    private void vhaccelerator$profileSpriteLoading(
            ResourceManager resourceManager,
            Stream<ResourceLocation> materials,
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfoReturnable<TextureAtlas.Preparations> callback
    ) {
        vhaccelerator$transitionTo("sprite-loading");
    }

    @Inject(method = "prepareToStitch", at = @At("RETURN"))
    private void vhaccelerator$reportAtlasProfile(
            ResourceManager resourceManager,
            Stream<ResourceLocation> materials,
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfoReturnable<TextureAtlas.Preparations> callback
    ) {
        if (!vhaccelerator$profilePreparation
                || vhaccelerator$stageNanos == null) {
            return;
        }
        vhaccelerator$finishCurrentStage();
        long total = vhaccelerator$stageNanos.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();
        StringBuilder report = new StringBuilder();
        vhaccelerator$stageNanos.forEach((stage, nanos) -> {
            if (!report.isEmpty()) {
                report.append(", ");
            }
            report.append(stage)
                    .append('=')
                    .append(vhaccelerator$millis(nanos))
                    .append(" ms");
        });
        VHAccelerator.LOGGER.info(
                "Texture atlas {} preparation: total={} ms [{}]",
                location,
                vhaccelerator$millis(total),
                report
        );
        vhaccelerator$stageNanos = null;
        vhaccelerator$currentStage = null;
        vhaccelerator$stageStarted = 0L;
    }

    @Unique
    private void vhaccelerator$finishCurrentStage() {
        if (vhaccelerator$currentStage == null
                || vhaccelerator$stageStarted == 0L) {
            return;
        }
        vhaccelerator$stageNanos.merge(
                vhaccelerator$currentStage,
                System.nanoTime() - vhaccelerator$stageStarted,
                Long::sum
        );
    }

    @Unique
    private void vhaccelerator$transitionTo(String nextStage) {
        if (!vhaccelerator$profilePreparation) {
            return;
        }
        vhaccelerator$finishCurrentStage();
        vhaccelerator$currentStage = nextStage;
        vhaccelerator$stageStarted = System.nanoTime();
    }

    @Unique
    private static String vhaccelerator$millis(long nanos) {
        return String.format(
                Locale.ROOT,
                "%.1f",
                nanos / 1_000_000.0
        );
    }
}
