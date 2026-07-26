package dev.hoyin1600p.launchfastertoo;

import com.mojang.logging.LogUtils;
import dev.hoyin1600p.launchfastertoo.client.LaunchFasterTooClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

@Mod(LaunchFasterToo.MOD_ID)
public final class LaunchFasterToo {
    public static final String MOD_ID = "launchfastertoo";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LaunchFasterToo() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, LaunchFasterTooConfig.COMMON_SPEC);

        // Keep every reference to client-only Minecraft classes behind this physical-side gate.
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> LaunchFasterTooClient::initialize);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);

        LOGGER.info("LaunchFasterToo loaded");
    }

    private void onServerStarted(ServerStartedEvent event) {
        ServerLaunchTimer.markEnd();
    }
}
