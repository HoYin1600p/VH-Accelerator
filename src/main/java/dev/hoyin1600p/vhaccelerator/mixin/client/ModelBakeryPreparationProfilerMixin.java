package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.LaunchTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelBakery.class, priority = 2_000)
public abstract class ModelBakeryPreparationProfilerMixin {
    @Unique
    private boolean vhaccelerator$profilePreparation;
    @Unique
    private long vhaccelerator$stageStarted;
    @Unique
    private String vhaccelerator$currentStage;
    @Unique
    private Map<String, Long> vhaccelerator$stageNanos;

    @Inject(method = "processLoading", at = @At("HEAD"), remap = false)
    private void vhaccelerator$beginPreparationProfile(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$profilePreparation =
                VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.profileClientLaunchPhases
                );
        if (!vhaccelerator$profilePreparation) {
            return;
        }
        vhaccelerator$stageNanos = new LinkedHashMap<>();
        vhaccelerator$currentStage =
                "preparation-and-missing-model";
        vhaccelerator$stageStarted = System.nanoTime();
    }

    @Inject(
            method = "processLoading",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/"
                            + "ProfilerFiller;popPush("
                            + "Ljava/lang/String;)V",
                    ordinal = 0
            ),
            require = 0
    )
    private void vhaccelerator$profileStaticDefinitions(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$transitionTo("static_definitions");
    }

    @Inject(
            method = "processLoading",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/"
                            + "ProfilerFiller;popPush("
                            + "Ljava/lang/String;)V",
                    ordinal = 1
            ),
            require = 0
    )
    private void vhaccelerator$profileBlocks(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$transitionTo("blocks");
    }

    @Inject(
            method = "processLoading",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/"
                            + "ProfilerFiller;popPush("
                            + "Ljava/lang/String;)V",
                    ordinal = 2
            ),
            require = 0
    )
    private void vhaccelerator$profileItems(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$transitionTo("items");
    }

    @Inject(
            method = "processLoading",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/"
                            + "ProfilerFiller;popPush("
                            + "Ljava/lang/String;)V",
                    ordinal = 3
            ),
            require = 0
    )
    private void vhaccelerator$profileSpecialModels(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$transitionTo("special");
    }

    @Inject(
            method = "processLoading",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/"
                            + "ProfilerFiller;popPush("
                            + "Ljava/lang/String;)V",
                    ordinal = 4
            ),
            require = 0
    )
    private void vhaccelerator$profileMaterials(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$transitionTo("textures");
    }

    @Inject(
            method = "processLoading",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/"
                            + "ProfilerFiller;popPush("
                            + "Ljava/lang/String;)V",
                    ordinal = 5
            ),
            require = 0
    )
    private void vhaccelerator$profileAtlasPreparation(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$transitionTo("stitching");
    }

    @Inject(method = "processLoading", at = @At("TAIL"), remap = false)
    private void vhaccelerator$reportPreparationProfile(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
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
                "ModelBakery {} preparation phases: total={} ms [{}]",
                LaunchTimer.isFinished()
                        ? "resource-reload"
                        : "initial",
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
        vhaccelerator$currentStage =
                vhaccelerator$displayName(nextStage);
        vhaccelerator$stageStarted = System.nanoTime();
    }

    @Unique
    private static String vhaccelerator$displayName(
            String profilerName
    ) {
        return switch (profilerName) {
            case "static_definitions" -> "static-definitions";
            case "blocks" -> "block-discovery";
            case "items" -> "item-discovery";
            case "special" -> "special-models";
            case "textures" -> "material-resolution";
            case "stitching" -> "atlas-preparation";
            default -> profilerName.replace('_', '-');
        };
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
