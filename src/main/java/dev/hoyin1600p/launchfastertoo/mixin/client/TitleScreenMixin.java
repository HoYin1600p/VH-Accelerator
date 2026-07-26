package dev.hoyin1600p.launchfastertoo.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hoyin1600p.launchfastertoo.client.LaunchFasterTooClientConfig;
import dev.hoyin1600p.launchfastertoo.client.LaunchTimer;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.internal.BrandingControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/internal/BrandingControl;"
                            + "forEachAboveCopyrightLine(Ljava/util/function/BiConsumer;)V",
                    remap = false
            )
    )
    private void launchfastertoo$drawLaunchTime(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callback
    ) {
        if (!LaunchTimer.isFinished()
                || !LaunchFasterTooClientConfig.VALUES.showLaunchTimer.get()) {
            return;
        }

        int[] brandingLines = {0};
        BrandingControl.forEachLine(true, true, (line, text) -> brandingLines[0] = line + 1);
        String launchText = String.format(
                "Launched in %.2fs",
                LaunchTimer.elapsedMillis() / 1000.0
        );
        int y = height - (10 + brandingLines[0] * 10);
        GuiComponent.drawString(poseStack, font, launchText, 2, y, 0x55FF55);
    }
}

