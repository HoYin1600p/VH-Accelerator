package dev.hoyin1600p.vhaccelerator.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import dev.hoyin1600p.vhaccelerator.client.ClientConnectionProfiler;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Separates LevelRenderer's first world pass from the surrounding
 * GameRenderer and shader-pipeline work.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererTimingMixin {
    @Unique
    private long vhaccelerator$firstLevelRenderStarted = -1L;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void vhaccelerator$beginFirstLevelRender(
            PoseStack poseStack,
            float partialTick,
            long finishTimeNanos,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightTexture lightTexture,
            Matrix4f projection,
            CallbackInfo callback
    ) {
        vhaccelerator$firstLevelRenderStarted =
                ClientConnectionProfiler.beginLevelRender();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void vhaccelerator$finishFirstLevelRender(
            PoseStack poseStack,
            float partialTick,
            long finishTimeNanos,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightTexture lightTexture,
            Matrix4f projection,
            CallbackInfo callback
    ) {
        long started = vhaccelerator$firstLevelRenderStarted;
        vhaccelerator$firstLevelRenderStarted = -1L;
        ClientConnectionProfiler.finishLevelRender(started);
    }
}
