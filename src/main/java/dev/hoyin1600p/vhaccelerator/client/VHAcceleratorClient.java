package dev.hoyin1600p.vhaccelerator.client;

import com.mojang.realmsclient.RealmsMainScreen;
import dev.hoyin1600p.vhaccelerator.ConfigMigration;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorCommand;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.ClientAssetFingerprint;
import dev.hoyin1600p.vhaccelerator.client.compat.ironfurnaces.IronFurnacesRecipeCache;
import dev.hoyin1600p.vhaccelerator.client.cache.LoginStateFingerprint;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentBlockStateJsonCache;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentModelJsonCache;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentModelMaterialCache;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.AdaptiveJeiWorkScheduler;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.PersistentVanillaIngredientCache;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.PersistentRecipeValidationCache;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.PersistentJeiRecipeIndexCache;
import dev.hoyin1600p.vhaccelerator.client.compat.jer.JerCompatibilityCache;
import dev.hoyin1600p.vhaccelerator.client.compat.thermal.PersistentStirlingFuelCache;
import dev.hoyin1600p.vhaccelerator.client.compat.xaero.XaeroOnlineCheckDeferrer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ScreenOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.internal.BrandingControl;

public final class VHAcceleratorClient {
    private static boolean ironFurnacesLoaded;
    private static boolean jerLoaded;
    private static boolean thermalLoaded;

    private VHAcceleratorClient() {
    }

    public static void initialize() {
        ConfigMigration.migrateClient();
        VHAcceleratorClientConfig.captureLaunchSnapshot();
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
        MinecraftForge.EVENT_BUS.addListener(
                VHAcceleratorClient::onRegisterClientCommands
        );
        ironFurnacesLoaded = ModList.get().isLoaded("ironfurnaces");
        jerLoaded = ModList.get().isLoaded("jeresources");
        thermalLoaded = ModList.get().isLoaded("thermal");
        if (VHAcceleratorClientConfig.optimizationsEnabled()) {
            AdaptiveJeiWorkScheduler.initialize();
            PersistentModelJsonCache.prewarm();
            PersistentModelMaterialCache.prewarm();
            PersistentBlockStateJsonCache.prewarm();
            PersistentVanillaIngredientCache.prewarm();
            PersistentRecipeValidationCache.prewarm();
            PersistentJeiRecipeIndexCache.prewarm();
            ClientAssetFingerprint.prewarm();
            if (thermalLoaded) {
                PersistentStirlingFuelCache.prewarm();
            }
        }
    }

    private static void onRegisterClientCommands(
            RegisterClientCommandsEvent event
    ) {
        VHAcceleratorCommand.register(event.getDispatcher(), false);
    }

    private static void onScreenOpened(ScreenOpenEvent event) {
        if (event.getScreen() instanceof ConnectScreen) {
            ClientWorkSession.begin();
            beginServerStateRefresh();
            if (VHAcceleratorClientConfig.optimizationsEnabled()) {
                AdaptiveJeiWorkScheduler.markLoading();
            }
            ServerTransferTimer.cancelActiveAttempt();
            ServerLoginTimer.markStart();
        } else if (event.getScreen() instanceof ReceivingLevelScreen
                && !ServerLoginTimer.isActive()
                && !ServerTransferTimer.isActive()) {
            ClientWorkSession.begin();
            beginServerStateRefresh();
            if (VHAcceleratorClientConfig.optimizationsEnabled()) {
                AdaptiveJeiWorkScheduler.markLoading();
            }
            ServerTransferTimer.markStart("receiving-level screen");
        }

        if (event.getScreen() instanceof JoinMultiplayerScreen
                || event.getScreen() instanceof TitleScreen
                || event.getScreen() instanceof RealmsMainScreen) {
            DisconnectTimer.finishMenuTransition(
                    event.getScreen().getClass().getSimpleName()
            );
        }
    }

    public static void beginServerStateRefresh() {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()) {
            return;
        }
        LoginStateFingerprint.beginConnection();
        IronFurnacesRecipeCache.beginConnection();
        PersistentVanillaIngredientCache.beginConnection();
        PersistentRecipeValidationCache.beginConnection();
        PersistentJeiRecipeIndexCache.beginConnection();
    }

    private static void onScreenDrawn(ScreenEvent.DrawScreenEvent.Post event) {
        releaseDeferredOnlineChecksFromMenu(event);
        runMenuPrecompile(event);
        if (!(event.getScreen() instanceof TitleScreen)
                || !LaunchTimer.isFinished()
                || !VHAcceleratorConfig.timersEnabled()) {
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
        PostLoginWorkTimer.Sample lastPostLogin = PostLoginWorkTimer.lastSample();
        DisconnectTimer.Sample lastDisconnect = DisconnectTimer.lastSample();
        StringBuilder launchText = new StringBuilder(String.format(
                "VH Accelerator%s: Launch %.2fs",
                VHAcceleratorConfig.compareModeEnabled()
                        ? " [COMPARE]"
                        : "",
                LaunchTimer.elapsedMillis() / 1000.0
        ));
        appendPrecompileStatus(launchText);
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
        if (lastPostLogin != null) {
            launchText.append(String.format(
                    " | Last post-login %.2fs",
                    lastPostLogin.totalMillis() / 1000.0
            ));
        }
        if (lastDisconnect != null) {
            launchText.append(String.format(
                    " | Last disconnect %.2fs",
                    lastDisconnect.totalMillis() / 1000.0
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

    private static void releaseDeferredOnlineChecksFromMenu(
            ScreenEvent.DrawScreenEvent.Post event
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!LaunchTimer.isFinished()
                || minecraft.level != null
                || minecraft.getConnection() != null
                || event.getScreen() instanceof ConnectScreen
                || event.getScreen() instanceof ReceivingLevelScreen) {
            return;
        }
        XaeroOnlineCheckDeferrer.releaseAfterUsableFrame();
    }

    private static void runMenuPrecompile(ScreenEvent.DrawScreenEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!LaunchTimer.isFinished()
                || !VHAcceleratorClientConfig.optimizationsEnabled()
                || minecraft.level != null
                || minecraft.getConnection() != null
                || event.getScreen() instanceof ConnectScreen
                || event.getScreen() instanceof ReceivingLevelScreen) {
            return;
        }

        if (event.getScreen() instanceof TitleScreen) {
            if (ironFurnacesLoaded
                    && VHAcceleratorClientConfig.VALUES
                            .cacheIronFurnacesJeiRecipes
                            .get()
                    && VHAcceleratorClientConfig.VALUES
                            .precompileIronFurnacesJeiRecipes
                            .get()) {
                IronFurnacesRecipeCache.beginMenuPrecompile();
            }
            if (jerLoaded
                    && VHAcceleratorClientConfig.VALUES
                            .cacheJerCompatibility
                            .get()) {
                JerCompatibilityCache.beginMenuPreload();
            }
        }

        if (jerLoaded
                && VHAcceleratorClientConfig.VALUES.cacheJerCompatibility.get()) {
            JerCompatibilityCache.pollMenuPreload();
        }
        if (ironFurnacesLoaded
                && VHAcceleratorClientConfig.VALUES
                        .cacheIronFurnacesJeiRecipes
                        .get()
                && VHAcceleratorClientConfig.VALUES
                        .precompileIronFurnacesJeiRecipes
                        .get()) {
            IronFurnacesRecipeCache.runMenuPrecompileSlice(
                    VHAcceleratorClientConfig.VALUES
                            .ironFurnacesPrecompileFrameBudgetMillis
                            .get()
            );
        }
    }

    private static void appendPrecompileStatus(StringBuilder text) {
        int startedTasks = 0;
        int totalProgress = 0;
        long elapsedMillis = 0L;
        boolean running = false;
        boolean completed = false;
        boolean failed = false;

        if (jerLoaded
                && VHAcceleratorClientConfig.VALUES.cacheJerCompatibility.get()) {
            JerCompatibilityCache.PreloadStatus jerStatus =
                    JerCompatibilityCache.preloadStatus();
            if (jerStatus.phase()
                    != JerCompatibilityCache.PreloadPhase.NOT_STARTED) {
                startedTasks++;
                elapsedMillis = Math.max(
                        elapsedMillis,
                        jerStatus.elapsedMillis()
                );
                if (jerStatus.phase()
                        == JerCompatibilityCache.PreloadPhase.RUNNING) {
                    running = true;
                    totalProgress += jerStatus.percent();
                } else if (jerStatus.phase()
                        == JerCompatibilityCache.PreloadPhase.COMPLETED) {
                    completed = true;
                    totalProgress += 100;
                } else {
                    failed = true;
                    totalProgress += 100;
                }
            }
        }

        if (ironFurnacesLoaded
                && VHAcceleratorClientConfig.VALUES
                        .cacheIronFurnacesJeiRecipes
                        .get()
                && VHAcceleratorClientConfig.VALUES
                        .precompileIronFurnacesJeiRecipes
                        .get()) {
            IronFurnacesRecipeCache.PrecompileStatus status =
                    IronFurnacesRecipeCache.precompileStatus();
            if (status.phase()
                    != IronFurnacesRecipeCache.PrecompilePhase.NOT_STARTED) {
                startedTasks++;
                elapsedMillis = Math.max(
                        elapsedMillis,
                        status.elapsedMillis()
                );
                if (status.phase()
                        == IronFurnacesRecipeCache.PrecompilePhase.RUNNING) {
                    running = true;
                    totalProgress += status.percent();
                } else if (status.phase()
                        == IronFurnacesRecipeCache.PrecompilePhase.COMPLETED) {
                    completed = true;
                    totalProgress += 100;
                } else {
                    failed = true;
                    totalProgress += 100;
                }
            }
        }

        if (startedTasks == 0) {
            return;
        }
        if (running) {
            int progress = Math.min(
                    99,
                    Math.round(totalProgress / (float) startedTasks)
            );
            text.append(String.format(" | Menu prep: %d%%", progress));
        } else if (failed) {
            text.append(completed
                    ? " | Menu prep partially completed"
                    : " | Menu prep deferred");
        } else if (elapsedMillis < 1000L) {
            text.append(String.format(
                    " | Menu prep completed in %dms",
                    elapsedMillis
            ));
        } else {
            text.append(String.format(
                    " | Menu prep completed in %.2fs",
                    elapsedMillis / 1000.0
            ));
        }
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
        ClientWorkSession.invalidate("Forge player logout");
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

        XaeroOnlineCheckDeferrer.releaseAfterUsableFrame();
        if (VHAcceleratorClientConfig.optimizationsEnabled()) {
            AdaptiveJeiWorkScheduler.markGameplayActive();
        }
        PostLoginWorkTimer.markFirstPlayableFrame();
        ServerLoginTimer.Sample loginSample = ServerLoginTimer.markFirstPlayableFrame();
        ServerTransferTimer.Sample transferSample =
                ServerTransferTimer.markFirstPlayableFrame();
        PostLoginWorkTimer.Sample postLoginSample =
                PostLoginWorkTimer.claimCompletedSample();
        if (!VHAcceleratorConfig.timersEnabled()) {
            return;
        }

        if (loginSample != null) {
            StringBuilder text = new StringBuilder(String.format(
                    "[VH Accelerator%s] Launch: %.2fs | Server login: %.2fs",
                    VHAcceleratorConfig.compareModeEnabled()
                            ? " Compare"
                            : "",
                    LaunchTimer.elapsedMillis() / 1000.0,
                    loginSample.totalMillis() / 1000.0
            ));
            appendPostLoginStatus(text, postLoginSample);
            minecraft.player.displayClientMessage(
                    new TextComponent(text.toString()).withStyle(ChatFormatting.GREEN),
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
        } else if (postLoginSample != null) {
            showPostLoginMessage(minecraft, postLoginSample);
        }
    }

    private static void showLaunchOnlyMessage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!LaunchTimer.claimChatMessage() || minecraft.player == null) {
            return;
        }

        StringBuilder text = new StringBuilder(String.format(
                "[VH Accelerator%s] Launch: %.2fs",
                VHAcceleratorConfig.compareModeEnabled()
                        ? " Compare"
                        : "",
                LaunchTimer.elapsedMillis() / 1000.0
        ));
        appendPostLoginStatus(text, null);
        minecraft.player.displayClientMessage(
                new TextComponent(text.toString()).withStyle(ChatFormatting.GREEN),
                false
        );
    }

    private static void appendPostLoginStatus(
            StringBuilder text,
            PostLoginWorkTimer.Sample completedSample
    ) {
        if (completedSample != null) {
            text.append(String.format(
                    " | Post-login: %.2fs",
                    completedSample.totalMillis() / 1000.0
            ));
        } else if (PostLoginWorkTimer.isRunning()) {
            text.append(" | Post-login: running");
        }
    }

    private static void showPostLoginMessage(
            Minecraft minecraft,
            PostLoginWorkTimer.Sample sample
    ) {
        String text = String.format(
                "[VH Accelerator] Post-login work completed in %.2fs",
                sample.totalMillis() / 1000.0
        );
        minecraft.player.displayClientMessage(
                new TextComponent(text).withStyle(ChatFormatting.GREEN),
                false
        );
    }
}
