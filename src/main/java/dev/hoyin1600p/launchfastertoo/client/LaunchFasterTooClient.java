package dev.hoyin1600p.launchfastertoo.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class LaunchFasterTooClient {
    private LaunchFasterTooClient() {
    }

    public static void initialize() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LaunchFasterTooClientConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(LaunchFasterTooClient::onPlayerLoggedIn);
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
