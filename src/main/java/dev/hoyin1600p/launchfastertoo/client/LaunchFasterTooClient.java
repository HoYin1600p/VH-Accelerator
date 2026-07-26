package dev.hoyin1600p.launchfastertoo.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ScreenOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.internal.BrandingControl;

public final class LaunchFasterTooClient {
    private LaunchFasterTooClient() {
    }

    public static void initialize() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LaunchFasterTooClientConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(LaunchFasterTooClient::onScreenOpened);
        MinecraftForge.EVENT_BUS.addListener(LaunchFasterTooClient::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(LaunchFasterTooClient::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(LaunchFasterTooClient::onLevelRendered);
        MinecraftForge.EVENT_BUS.addListener(LaunchFasterTooClient::onScreenDrawn);
    }

    private static void onScreenOpened(ScreenOpenEvent event) {
        if (event.getScreen() instanceof ConnectScreen) {
            ServerLoginTimer.markStart();
        }
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
        ServerLoginTimer.Sample lastLogin = ServerLoginTimer.lastSample();
        String launchText = lastLogin == null
                ? String.format(
                        "LaunchFasterToo: Launch %.2fs",
                        LaunchTimer.elapsedMillis() / 1000.0
                )
                : String.format(
                        "LaunchFasterToo: Launch %.2fs | Last server login %.2fs",
                        LaunchTimer.elapsedMillis() / 1000.0,
                        lastLogin.totalMillis() / 1000.0
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
        if (event.getPlayer() == null) {
            return;
        }

        if (ServerLoginTimer.markPlayerReady()) {
            return;
        }

        showLaunchOnlyMessage();
    }

    private static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        ServerLoginTimer.cancelActiveAttempt();
    }

    private static void onLevelRendered(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER
                || minecraft.level == null
                || minecraft.player == null
                || minecraft.screen instanceof ReceivingLevelScreen) {
            return;
        }

        ServerLoginTimer.Sample sample = ServerLoginTimer.markFirstPlayableFrame();
        if (sample == null
                || !LaunchFasterTooClientConfig.VALUES.showLaunchTimer.get()) {
            return;
        }

        String text = String.format(
                "[LaunchFasterToo] Launch: %.2fs | Server login: %.2fs",
                LaunchTimer.elapsedMillis() / 1000.0,
                sample.totalMillis() / 1000.0
        );
        minecraft.player.displayClientMessage(
                new TextComponent(text).withStyle(ChatFormatting.GREEN),
                false
        );
    }

    private static void showLaunchOnlyMessage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!LaunchTimer.claimChatMessage() || minecraft.player == null) {
            return;
        }

        String text = String.format(
                "[LaunchFasterToo] Launch: %.2fs",
                LaunchTimer.elapsedMillis() / 1000.0
        );
        minecraft.player.displayClientMessage(
                new TextComponent(text).withStyle(ChatFormatting.GREEN),
                false
        );
    }
}
