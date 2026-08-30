package dev.hoyin1600p.vhaccelerator;

import com.mojang.logging.LogUtils;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.entity.AttributeInstanceTemplates;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.entity.AttributeSupplierDeduplication;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(VHAccelerator.MOD_ID)
public final class VHAccelerator {
    public static final String MOD_ID = "vhaccelerator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VHAccelerator() {
        ConfigMigration.migrateCommon();
        BootstrapCompareMode.capture();
        BootstrapDebugDiagnostics.capture();
        BootstrapBackportConfig.capture();
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON,
                VHAcceleratorConfig.COMMON_SPEC,
                ConfigMigration.COMMON_CONFIG
        );
        FMLJavaModLoadingContext.get().getModEventBus()
                .addListener(this::onLoadComplete);

        // Keep every reference to client-only Minecraft classes behind this physical-side gate.
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> VHAcceleratorClient::initialize);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        }

        LOGGER.info("VH Accelerator loaded");
    }

    private void onServerStarted(ServerStartedEvent event) {
        ServerLaunchTimer.markEnd();
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        AttributeSupplierDeduplication.closeStartupWindow();
        AttributeInstanceTemplates.Statistics statistics =
                AttributeInstanceTemplates.statistics();
        if (statistics.requests() != 0) {
            LOGGER.info(
                    "Attribute-supplier startup deduplication retained {} "
                            + "unique templates and reused {} duplicates",
                    statistics.uniqueTemplates(),
                    statistics.reusedTemplates()
            );
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        VHAcceleratorCommand.register(event.getDispatcher(), true);
    }
}
