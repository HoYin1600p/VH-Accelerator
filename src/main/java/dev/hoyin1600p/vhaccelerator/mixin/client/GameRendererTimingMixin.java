package dev.hoyin1600p.vhaccelerator.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hoyin1600p.vhaccelerator.client.ClientConnectionProfiler;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the complete first world-render pass, including shader-pipeline
 * setup that occurs outside LevelRenderer itself.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererTimingMixin {
    @Unique
    private long vhaccelerator$firstWorldRenderStarted = -1L;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void vhaccelerator$beginFirstWorldRender(
            float partialTick,
            long finishTimeNanos,
            PoseStack poseStack,
            CallbackInfo callback
    ) {
        vhaccelerator$firstWorldRenderStarted =
                ClientConnectionProfiler.beginFirstWorldRender();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void vhaccelerator$finishFirstWorldRender(
            float partialTick,
            long finishTimeNanos,
            PoseStack poseStack,
            CallbackInfo callback
    ) {
        long started = vhaccelerator$firstWorldRenderStarted;
        vhaccelerator$firstWorldRenderStarted = -1L;
        ClientConnectionProfiler.finishFirstWorldRender(started);
    }
}
