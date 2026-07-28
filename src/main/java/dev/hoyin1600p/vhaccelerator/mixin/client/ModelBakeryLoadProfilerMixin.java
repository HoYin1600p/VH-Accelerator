package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.LaunchTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Separates model discovery by namespace and resource kind so a custom client
 * does not accidentally dictate which mods receive compatibility work.
 */
@Mixin(value = ModelBakery.class, priority = 2_000)
public abstract class ModelBakeryLoadProfilerMixin {
    private static final int REPORT_LIMIT = 16;

    @Unique
    private boolean vhaccelerator$profileLoads;
    @Unique
    private long vhaccelerator$loadStarted;
    @Unique
    private String vhaccelerator$currentLoad;
    @Unique
    private Map<String, long[]> vhaccelerator$loadTimings;

    @Inject(method = "processLoading", at = @At("HEAD"), remap = false)
    private void vhaccelerator$beginLoadProfile(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$profileLoads =
                VHAcceleratorClientConfig.launchProfilingEnabled();
        if (vhaccelerator$profileLoads) {
            vhaccelerator$loadTimings = new HashMap<>();
        }
    }

    @Inject(method = "loadModel", at = @At("HEAD"))
    private void vhaccelerator$beginModelLoad(
            ResourceLocation location,
            CallbackInfo callback
    ) {
        if (!vhaccelerator$profileLoads) {
            return;
        }
        vhaccelerator$currentLoad =
                location.getNamespace()
                        + '\u0000'
                        + vhaccelerator$kind(location);
        vhaccelerator$loadStarted = System.nanoTime();
    }

    @Inject(method = "loadModel", at = @At("RETURN"))
    private void vhaccelerator$finishModelLoad(
            ResourceLocation location,
            CallbackInfo callback
    ) {
        Map<String, long[]> timings = vhaccelerator$loadTimings;
        if (!vhaccelerator$profileLoads
                || timings == null
                || vhaccelerator$currentLoad == null
                || vhaccelerator$loadStarted == 0L) {
            return;
        }
        long elapsed = System.nanoTime()
                - vhaccelerator$loadStarted;
        long[] timing = timings.computeIfAbsent(
                vhaccelerator$currentLoad,
                ignored -> new long[3]
        );
        timing[0] += elapsed;
        timing[1]++;
        timing[2] = Math.max(timing[2], elapsed);
        vhaccelerator$currentLoad = null;
        vhaccelerator$loadStarted = 0L;
    }

    @Inject(method = "processLoading", at = @At("TAIL"), remap = false)
    private void vhaccelerator$reportLoadProfile(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        Map<String, long[]> timings = vhaccelerator$loadTimings;
        vhaccelerator$profileLoads = false;
        vhaccelerator$loadTimings = null;
        vhaccelerator$currentLoad = null;
        vhaccelerator$loadStarted = 0L;

        if (timings == null || timings.isEmpty()) {
            return;
        }

        List<Map.Entry<String, long[]>> entries =
                new ArrayList<>(
                        timings.entrySet()
                );
        entries.sort(Comparator.comparingLong(
                (Map.Entry<String, long[]> entry) ->
                        entry.getValue()[0]
        ).reversed());
        long total = entries.stream()
                .mapToLong(entry -> entry.getValue()[0])
                .sum();

        VHAccelerator.LOGGER.info(
                "ModelBakery {} discovery attributed {} ms across {} "
                        + "namespace/kind pair(s); showing the slowest {}",
                LaunchTimer.isFinished()
                        ? "resource-reload"
                        : "initial",
                vhaccelerator$millis(total),
                entries.size(),
                Math.min(REPORT_LIMIT, entries.size())
        );
        for (int index = 0;
             index < Math.min(REPORT_LIMIT, entries.size());
             index++) {
            Map.Entry<String, long[]> entry =
                    entries.get(index);
            int separator = entry.getKey().indexOf('\u0000');
            String namespace = entry.getKey().substring(
                    0,
                    separator
            );
            String kind = entry.getKey().substring(separator + 1);
            VHAccelerator.LOGGER.info(
                    "Model discovery [{}] {} {}: {} ms across {} load(s), "
                            + "max {} ms",
                    index + 1,
                    namespace,
                    kind,
                    vhaccelerator$millis(entry.getValue()[0]),
                    entry.getValue()[1],
                    vhaccelerator$millis(
                            entry.getValue()[2]
                    )
            );
        }

    }

    @Unique
    private static String vhaccelerator$kind(
            ResourceLocation location
    ) {
        if (location instanceof ModelResourceLocation modelLocation) {
            return "inventory".equals(modelLocation.getVariant())
                    ? "item"
                    : "blockstate";
        }
        return "model";
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
