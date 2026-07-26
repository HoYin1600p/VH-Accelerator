package dev.hoyin1600p.launchfastertoo.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.internal.BrandingControl;

public final class LaunchFasterTooClient {
    private LaunchFasterTooClient() {
    }

    public static void initialize() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LaunchFasterTooClientConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(LaunchFasterTooClient::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(LaunchFasterTooClient::onScreenDrawn);
    }

    private static void onScreenDrawn(ScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)
                || !LaunchTimer.isFinished()
                || !LaunchFasterTooClientConfig.VALUES.showLaunchTimer.get()) {
            return;
        }

        int[] brandingLines = {0};
        BrandingControl.forEachLine(
                true,
                true,
                (line, text) -> brandingLines[0] = line + 1
        );
        String launchText = String.format(
                "LaunchFasterToo: %.2fs",
                LaunchTimer.elapsedMillis() / 1000.0
        );
        int y = event.getScreen().height - (10 + brandingLines[0] * 10);

        event.getPoseStack().pushPose();
        GuiComponent.drawString(
                event.getPoseStack(),
                Minecraft.getInstance().font,
                launchText,
                2,
                y,
                0x55FF55
        );
        event.getPoseStack().popPose();
    }

    private static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggedInEvent event) {
        if (!LaunchTimer.claimChatMessage() || event.getPlayer() == null) {
            return;
        }

        String text = String.format(
                "[LaunchFasterToo] Game launched in %.2f seconds",
                LaunchTimer.elapsedMillis() / 1000.0
        );
        event.getPlayer().displayClientMessage(
                new TextComponent(text).withStyle(ChatFormatting.GREEN),
                false
        );
    }
}
