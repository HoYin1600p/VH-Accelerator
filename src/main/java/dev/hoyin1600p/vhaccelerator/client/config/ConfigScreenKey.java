package dev.hoyin1600p.vhaccelerator.client.config;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

/**
 * The only way to open the settings screen: an unbound key in Controls under the mod's category.
 * Cloth Config is optional; without it the key explains where the settings still live.
 *
 * Wiring:
 *   - FMLClientSetupEvent: {@code event.enqueueWork(ConfigScreenKey::register);}
 *   - mod constructor, inside {@code if (FMLEnvironment.dist.isClient())}:
 *     {@code MinecraftForge.EVENT_BUS.addListener(ConfigScreenKey::onClientTick);}
 */
public final class ConfigScreenKey {
    public static final String CLOTH_CONFIG_MOD_ID = "cloth_config";
    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.vhaccelerator.open_config",
            KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN,
            "key.categories.vhaccelerator"
    );

    private ConfigScreenKey() {
    }

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN_CONFIG);
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        while (OPEN_CONFIG.consumeClick()) {
            open();
        }
    }

    private static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ModList.get().isLoaded(CLOTH_CONFIG_MOD_ID)) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        new TranslatableComponent("vhaccelerator.config.message.cloth_missing"), false);
            }
            return;
        }
        try {
            // Only reached with Cloth installed, so its classes resolve. Keep this a fully
            // qualified reference: no import of the cloth package anywhere outside it.
            dev.hoyin1600p.vhaccelerator.client.config.cloth.SettingsScreen.open(minecraft.screen);
        } catch (LinkageError incompatible) {
            // Built against Cloth 6.5.102; an older or changed Cloth API must not crash the game.
            VHAccelerator.LOGGER.warn("Could not open the settings screen with this Cloth Config", incompatible);
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        new TranslatableComponent("vhaccelerator.config.message.cloth_incompatible"), false);
            }
        }
    }
}
