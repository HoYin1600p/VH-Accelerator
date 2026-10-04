package dev.hoyin1600p.vhaccelerator.client;

import com.mojang.realmsclient.RealmsMainScreen;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorCommand;
import dev.hoyin1600p.vhaccelerator.bootstrap.ConfigMigration;
import dev.hoyin1600p.vhaccelerator.client.cache.ClientConfigReloadObserver;
import dev.hoyin1600p.vhaccelerator.client.cache.FerriteCoreQuadCacheCapacity;
import dev.hoyin1600p.vhaccelerator.client.cache.fingerprint.ClientAssetFingerprint;
import dev.hoyin1600p.vhaccelerator.client.cache.fingerprint.LoginStateFingerprint;
import dev.hoyin1600p.vhaccelerator.client.cache.persist.PersistentBlockStateJsonCache;
import dev.hoyin1600p.vhaccelerator.client.cache.persist.PersistentModelJsonCache;
import dev.hoyin1600p.vhaccelerator.client.cache.persist.PersistentModelMaterialCache;
import dev.hoyin1600p.vhaccelerator.client.compat.farsight.FarsightChunkBound;
import dev.hoyin1600p.vhaccelerator.client.compat.ironfurnaces.IronFurnacesRecipeCache;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.AdaptiveJeiWorkScheduler;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.JeiRecoveryReload;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.JeiRuntimeEpoch;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.PersistentJeiRecipeIndexCache;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.PersistentRecipeValidationCache;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.PersistentVanillaIngredientCache;
import dev.hoyin1600p.vhaccelerator.client.compat.jer.JerCompatibilityCache;
import dev.hoyin1600p.vhaccelerator.client.compat.thermal.PersistentStirlingFuelCache;
import dev.hoyin1600p.vhaccelerator.client.compat.xaero.XaeroOnlineCheckDeferrer;
import dev.hoyin1600p.vhaccelerator.client.config.ConfigScreenKey;
import dev.hoyin1600p.vhaccelerator.client.diagnostics.ClientTextureSafetyAudit;
import dev.hoyin1600p.vhaccelerator.client.model.DeferredBlockStateBaking;
import dev.hoyin1600p.vhaccelerator.client.model.parse.ModelLocationPaths;
import dev.hoyin1600p.vhaccelerator.client.profiling.DisconnectTimer;
import dev.hoyin1600p.vhaccelerator.client.profiling.LaunchTimer;
import dev.hoyin1600p.vhaccelerator.client.profiling.PostLoginWorkTimer;
import dev.hoyin1600p.vhaccelerator.client.profiling.ServerLoginTimer;
import dev.hoyin1600p.vhaccelerator.client.profiling.ServerTransferTimer;
import dev.hoyin1600p.vhaccelerator.client.update.UpdateNoticeFilter;
import dev.hoyin1600p.vhaccelerator.client.update.UpdateNoticeService;
import dev.hoyin1600p.vhaccelerator.compat.farsight.FarsightBoundOwner;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ScreenOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.internal.BrandingControl;

public final class VHAcceleratorClient {
    private static boolean ironFurnacesLoaded;
    private static boolean jerLoaded;
    private static boolean thermalLoaded;
    private static boolean ferriteCoreLoaded;

    private VHAcceleratorClient() {
    }

    public static void initialize() {
        ConfigMigration.migrateClient();
        VHAcceleratorClientConfig.captureLaunchSnapshot();
        UpdateNoticeService.initialize(
                VHAccelerator.MOD_ID,
                "VH Accelerator",
                "https://raw.githubusercontent.com/HoYin1600p/"
                        + "VH-Accelerator/master/update.json",
                "https://www.curseforge.com/minecraft/mc-mods/vh-accelerator",
                VHAcceleratorClientConfig.updateChecksEnabled(),
                VHAcceleratorClientConfig.updateNoticeFilter()
        );
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT,
                VHAcceleratorClientConfig.SPEC,
                ConfigMigration.CLIENT_CONFIG
        );
        FMLJavaModLoadingContext.get()
                .getModEventBus()
                .addListener(ClientTextureSafetyAudit::onTextureStitched);
        FMLJavaModLoadingContext.get()
                .getModEventBus()
                .addListener(ClientConfigReloadObserver::onLoadComplete);
        // Settings screen: an unbound key under VH Accelerator in Controls.
        FMLJavaModLoadingContext.get()
                .getModEventBus()
                .addListener((FMLClientSetupEvent event) -> event.enqueueWork(ConfigScreenKey::register));
        MinecraftForge.EVENT_BUS.addListener(ConfigScreenKey::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(VHAcceleratorClient::onScreenOpened);
        MinecraftForge.EVENT_BUS.addListener(SingleplayerLevelPruner::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(VHAcceleratorClient::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(VHAcceleratorClient::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(VHAcceleratorClient::onLevelRendered);
        MinecraftForge.EVENT_BUS.addListener(VHAcceleratorClient::onScreenDrawn);
        MinecraftForge.EVENT_BUS.addListener(
                VHAcceleratorClient::onRegisterClientCommands
        );
        // Ahead of JEI, which starts from this event on a join.
        MinecraftForge.EVENT_BUS.addListener(
                net.minecraftforge.eventbus.api.EventPriority.HIGHEST,
                false,
                net.minecraftforge.event.TagsUpdatedEvent.class,
                VHAcceleratorClient::onTagsUpdated
        );
        ironFurnacesLoaded = ModList.get().isLoaded("ironfurnaces");
        jerLoaded = ModList.get().isLoaded("jeresources");
        thermalLoaded = ModList.get().isLoaded("thermal");
        ferriteCoreLoaded = ModList.get().isLoaded("ferritecore");
        ModelLocationPaths.configure(
                VHAcceleratorClientConfig.optimizationsEnabled()
                        && VHAcceleratorClientConfig.launchValue(
                                VHAcceleratorClientConfig.VALUES.deduplicateModelLocationPaths, true));
        if (FarsightBoundOwner.vhaOwnsBound(
                net.minecraftforge.fml.loading.LoadingModList.get())) {
            // Same gate as the mixin plugin: VRO owns this once it declares it.
            MinecraftForge.EVENT_BUS.addListener(FarsightChunkBound::onClientTick);
        }
        if (VHAcceleratorClientConfig.optimizationsEnabled()) {
            // Every persistent login cache is consumed during JEI start.
            JeiRuntimeEpoch.setAfterStart(VHAcceleratorClient::releaseLoginCacheMemory);
            AdaptiveJeiWorkScheduler.initialize();
            PersistentModelJsonCache.prewarm();
            PersistentModelMaterialCache.prewarm();
            PersistentBlockStateJsonCache.prewarm();
            prewarmLoginCaches();
            ClientAssetFingerprint.prewarm();
            if (ferriteCoreLoaded) {
                FerriteCoreQuadCacheCapacity.prewarm();
            }
        } else if (VHAcceleratorConfig.compareModeEnabled()) {
            VHAccelerator.LOGGER.info(
                    "Compare Mode bootstrap audit: skipped all client "
                            + "optimization startup groups"
            );
        }
    }


    private static void onTagsUpdated(net.minecraftforge.event.TagsUpdatedEvent event) {
        if (event.getUpdateCause()
                == net.minecraftforge.event.TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED
                && Minecraft.getInstance().isSameThread()
                && VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.prefetchJoinRecipeFingerprint, true)) {
            LoginStateFingerprint.prefetchRecipeFingerprint();
        }
    }
    private static void onRegisterClientCommands(
            RegisterClientCommandsEvent event
    ) {
        VHAcceleratorCommand.registerClient(
                event.getDispatcher(),
                VHAcceleratorClient::reloadJei,
                VHAcceleratorClientConfig::updateChecksEnabled,
                VHAcceleratorClient::setUpdateChecksEnabled,
                VHAcceleratorClientConfig::updateNoticeFilter,
                VHAcceleratorClient::setUpdateNoticeFilter
        );
    }

    public static void setUpdateChecksEnabled(boolean enabled) {
        VHAcceleratorClientConfig.setUpdateChecksEnabled(enabled);
        UpdateNoticeService.setEnabled(enabled);
    }

    public static void setUpdateNoticeFilter(UpdateNoticeFilter filter) {
        VHAcceleratorClientConfig.setUpdateNoticeFilter(filter);
        UpdateNoticeService.setFilter(filter);
    }

    private static int reloadJei(
            CommandSourceStack source
    ) {
        source.sendSuccess(
                new TranslatableComponent("vhaccelerator.jei.reload.start")
                        .withStyle(ChatFormatting.YELLOW),
                false
        );
        JeiRecoveryReload.Result result = JeiRecoveryReload.reload();
        if (!result.successful()) {
            source.sendFailure(new TranslatableComponent(
                    "vhaccelerator.jei.reload.failed",
                    result.failureMessage()
            ));
            return 0;
        }

        source.sendSuccess(
                new TranslatableComponent(
                        "vhaccelerator.jei.reload.done",
                        seconds(result.elapsedMillis())
                ).withStyle(ChatFormatting.GREEN),
                false
        );
        return 1;
    }

    private static void onScreenOpened(ScreenOpenEvent event) {
        SingleplayerLevelPruner.onScreenOpened(event.getScreen());
        if (event.getScreen() instanceof ConnectScreen) {
            if (VHAcceleratorClientConfig.optimizationsEnabled()) {
                AdaptiveJeiWorkScheduler.markLoading();
            }
            ServerTransferTimer.cancelActiveAttempt();
            ServerLoginTimer.markStart();
        } else if (event.getScreen() instanceof ReceivingLevelScreen
                && !ServerLoginTimer.isActive()
                && !ServerTransferTimer.isActive()) {
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

    public static synchronized boolean observeConnection(net.minecraft.network.Connection connection) {
        if (connection == null || !connection.isConnected()) { return false; }
        if (ClientWorkSession.observeConnection(connection)) {
            LoginStateFingerprint.beginConnection();
            beginRecipeStateRefresh();
        }
        return ClientWorkSession.owns(connection);
    }

    public static synchronized void captureServerConfig(net.minecraft.network.Connection connection,
            String fileName, byte[] contents) {
        if (observeConnection(connection)) { LoginStateFingerprint.captureServerConfig(fileName, contents); }
    }

    public static synchronized boolean closeConnection(Object connection, String reason) {
        if (!ClientWorkSession.invalidate(connection, reason)) { return false; }
        LoginStateFingerprint.beginConnection();
        beginRecipeStateRefresh();
        return true;
    }

    public static void beginRecipeStateRefresh() {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()) {
            return;
        }
        LoginStateFingerprint.refreshLocalConfigs();
        IronFurnacesRecipeCache.beginConnection();
        PersistentVanillaIngredientCache.beginConnection();
        PersistentRecipeValidationCache.beginConnection();
        PersistentJeiRecipeIndexCache.beginConnection();
        // A same-address reconnect or proxy switch keeps its entries in
        // memory; another server's cache files are read in the background.
        String serverKey = LoginStateFingerprint.currentServerKey();
        PersistentVanillaIngredientCache.prewarmFor(serverKey);
        if (VHAcceleratorClientConfig.VALUES
                .persistentVanillaRecipeValidationCache
                .get()) {
            PersistentRecipeValidationCache.prewarmFor(serverKey);
        }
        PersistentJeiRecipeIndexCache.prewarmFor(serverKey);
        if (thermalLoaded) {
            PersistentStirlingFuelCache.prewarmFor(serverKey);
        }
        if (ironFurnacesLoaded) {
            IronFurnacesRecipeCache.prewarmFor(serverKey);
        }
    }

    private static void prewarmLoginCaches() {
        PersistentVanillaIngredientCache.prewarm();
        if (VHAcceleratorClientConfig.VALUES
                .persistentVanillaRecipeValidationCache
                .get()) {
            PersistentRecipeValidationCache.prewarm();
        }
        PersistentJeiRecipeIndexCache.prewarm();
        if (thermalLoaded) {
            PersistentStirlingFuelCache.prewarm();
        }
    }

    /**
     * After a JEI runtime has finished starting, which is when the per-server
     * login caches are consumed, keeps only the current server's entries in
     * memory and releases every other server's. Reconnects and proxy backend
     * switches to the same address therefore never reread from disk. The
     * files stay on disk.
     */
    public static void releaseLoginCacheMemory() {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.releaseCacheMemoryAfterUse
                )) {
            return;
        }
        String serverKey = LoginStateFingerprint.currentServerKey();
        PersistentVanillaIngredientCache.releaseMemory(serverKey);
        PersistentRecipeValidationCache.releaseMemory(serverKey);
        PersistentJeiRecipeIndexCache.releaseMemory(serverKey);
        if (thermalLoaded) {
            PersistentStirlingFuelCache.releaseMemory(serverKey);
        }
        if (ironFurnacesLoaded) {
            IronFurnacesRecipeCache.releaseMemory(serverKey);
        }
    }

    private static void onScreenDrawn(ScreenEvent.DrawScreenEvent.Post event) {
        releaseDeferredOnlineChecksFromMenu(event);
        runMenuPrecompile(event);
        if (!(event.getScreen() instanceof TitleScreen)
                || !LaunchTimer.isFinished()) {
            return;
        }

        int[] brandingLines = {0};
        BrandingControl.forEachLine(
                true,
                true,
                (line, text) -> brandingLines[0] = line + 1
        );
        String launchText = String.format(
                "VH Accelerator%s: Launch %.2fs",
                VHAcceleratorConfig.compareModeEnabled()
                        ? " [COMPARE]"
                        : "",
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
        DeferredBlockStateBaking.worldExited();
        if (jerLoaded
                && VHAcceleratorClientConfig.VALUES.cacheJerCompatibility.get()) {
            JerCompatibilityCache.releaseWorldReferences();
        }
        if (closeConnection(event.getConnection(), "Forge player logout")) {
            ServerLoginTimer.cancelActiveAttempt();
            ServerTransferTimer.cancelActiveAttempt();
        }
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
        DeferredBlockStateBaking.observePlayableFrame(minecraft.level);
        ServerLoginTimer.Sample loginSample = ServerLoginTimer.markFirstPlayableFrame();
        ServerTransferTimer.Sample transferSample =
                ServerTransferTimer.markFirstPlayableFrame();
        PostLoginWorkTimer.Sample postLoginSample =
                PostLoginWorkTimer.claimCompletedSample();
        if (!VHAcceleratorConfig.timersEnabled()) {
            return;
        }

        if (loginSample != null) {
            MutableComponent text = new TranslatableComponent(
                    "vhaccelerator.timer.launch_login",
                    compareSuffix(),
                    seconds(LaunchTimer.elapsedMillis()),
                    seconds(loginSample.totalMillis())
            ).withStyle(ChatFormatting.GREEN);
            appendPostLoginStatus(text, postLoginSample);
            minecraft.player.displayClientMessage(text, false);
        } else if (transferSample != null) {
            minecraft.player.displayClientMessage(
                    new TranslatableComponent(
                            "vhaccelerator.timer.transfer",
                            seconds(transferSample.totalMillis())
                    ).withStyle(ChatFormatting.GREEN),
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

        MutableComponent text = new TranslatableComponent(
                "vhaccelerator.timer.launch",
                compareSuffix(),
                seconds(LaunchTimer.elapsedMillis())
        ).withStyle(ChatFormatting.GREEN);
        appendPostLoginStatus(text, null);
        minecraft.player.displayClientMessage(text, false);
    }

    private static void appendPostLoginStatus(
            MutableComponent text,
            PostLoginWorkTimer.Sample completedSample
    ) {
        if (completedSample != null) {
            text.append(new TranslatableComponent(
                    "vhaccelerator.timer.post_login",
                    seconds(completedSample.totalMillis())
            ));
        } else if (PostLoginWorkTimer.isRunning()) {
            text.append(new TranslatableComponent("vhaccelerator.timer.post_login_running"));
        }
    }

    private static Component compareSuffix() {
        return VHAcceleratorConfig.compareModeEnabled()
                ? new TranslatableComponent("vhaccelerator.timer.compare_suffix")
                : new TextComponent("");
    }

    private static String seconds(double millis) {
        return String.format("%.2f", millis / 1000.0);
    }

    private static void showPostLoginMessage(
            Minecraft minecraft,
            PostLoginWorkTimer.Sample sample
    ) {
        minecraft.player.displayClientMessage(
                new TranslatableComponent(
                        "vhaccelerator.timer.post_login_done",
                        seconds(sample.totalMillis())
                ).withStyle(ChatFormatting.GREEN),
                false
        );
    }
}
