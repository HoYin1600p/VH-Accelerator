package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.ConfigMigration;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.JeiLifecycleBridge;
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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.internal.BrandingControl;

public final class VHAcceleratorClient {
    private VHAcceleratorClient() {
    }

    public static void initialize() {
        ConfigMigration.migrateClient();
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT,
                VHAcceleratorClientConfig.SPEC,
                ConfigMigration.CLIENT_CONFIG
        );
        MinecraftForge.EVENT_BUS.addListener(VHAcceleratorClient::onScreenOpened);
        MinecraftForge.EVENT_BUS.addListener(VHAcceleratorClient::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(VHAcceleratorClient::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(VHAcceleratorClient::onLevelRendered);
        MinecraftForge.EVENT_BUS.addListener(VHAcceleratorClient::onScreenDrawn);
    }

    private static void onScreenOpened(ScreenOpenEvent event) {
        if (event.getScreen() instanceof ConnectScreen) {
            ServerTransferTimer.cancelActiveAttempt();
            ServerLoginTimer.markStart();
        } else if (event.getScreen() instanceof ReceivingLevelScreen
                && !ServerLoginTimer.isActive()
                && !ServerTransferTimer.isActive()) {
            ServerTransferTimer.markStart("receiving-level screen");
        }
    }

    private static void onScreenDrawn(ScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)
                || !LaunchTimer.isFinished()
                || !VHAcceleratorClientConfig.VALUES.showLaunchTimer.get()) {
            return;
        }

        int[] brandingLines = {0};
        BrandingControl.forEachLine(
                true,
                true,
                (line, text) -> brandingLines[0] = line + 1
        );
        ServerLoginTimer.Sample lastLogin = ServerLoginTimer.lastSample();
        ServerTransferTimer.Sample lastTransfer = ServerTransferTimer.lastSample();
        StringBuilder launchText = new StringBuilder(String.format(
                "VH Accelerator: Launch %.2fs",
                LaunchTimer.elapsedMillis() / 1000.0
        ));
        if (lastLogin != null) {
            launchText.append(String.format(
                    " | Last server login %.2fs",
                    lastLogin.totalMillis() / 1000.0
            ));
        }
        if (lastTransfer != null) {
            launchText.append(String.format(
                    " | Last transfer %.2fs",
                    lastTransfer.totalMillis() / 1000.0
            ));
        }
        int y = event.getScreen().height - (10 + brandingLines[0] * 10);

        event.getPoseStack().pushPose();
        GuiComponent.drawString(
                event.getPoseStack(),
                Minecraft.getInstance().font,
                launchText.toString(),
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
        if (ModList.get().isLoaded("jei")) {
            JeiLifecycleBridge.onClientDisconnected();
        }
        ServerLoginTimer.cancelActiveAttempt();
        ServerTransferTimer.cancelActiveAttempt();
    }

    private static void onLevelRendered(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER
                || minecraft.level == null
                || minecraft.player == null
                || minecraft.screen instanceof ReceivingLevelScreen) {
            return;
        }

        ServerLoginTimer.Sample loginSample = ServerLoginTimer.markFirstPlayableFrame();
        ServerTransferTimer.Sample transferSample =
                ServerTransferTimer.markFirstPlayableFrame();
        if (transferSample != null && ModList.get().isLoaded("jei")) {
            JeiLifecycleBridge.recoverAfterTransfer();
        }
        if (!VHAcceleratorClientConfig.VALUES.showLaunchTimer.get()) {
            return;
        }

        if (loginSample != null) {
            String text = String.format(
                    "[VH Accelerator] Launch: %.2fs | Server login: %.2fs",
                    LaunchTimer.elapsedMillis() / 1000.0,
                    loginSample.totalMillis() / 1000.0
            );
            minecraft.player.displayClientMessage(
                    new TextComponent(text).withStyle(ChatFormatting.GREEN),
                    false
            );
        } else if (transferSample != null) {
            String text = String.format(
                    "[VH Accelerator] Server/world transfer: %.2fs",
                    transferSample.totalMillis() / 1000.0
            );
            minecraft.player.displayClientMessage(
                    new TextComponent(text).withStyle(ChatFormatting.GREEN),
                    false
            );
        }
    }

    private static void showLaunchOnlyMessage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!LaunchTimer.claimChatMessage() || minecraft.player == null) {
            return;
        }

        String text = String.format(
                "[VH Accelerator] Launch: %.2fs",
                LaunchTimer.elapsedMillis() / 1000.0
        );
        minecraft.player.displayClientMessage(
                new TextComponent(text).withStyle(ChatFormatting.GREEN),
                false
        );
    }
}
