package dev.hoyin1600p.vhaccelerator.client.compat.sophisticated;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Keeps Sophisticated Core's event-added container slot sprites in the block
 * atlas when model-loading optimizations alter resource reload scheduling.
 */
public final class SophisticatedSlotSpriteGuard {
    private static final String MOD_ID = "sophisticatedcore";
    private static final List<ResourceLocation> SLOT_SPRITES = List.of(
            sprite("empty_upgrade_slot"),
            sprite("inaccessible_slot"),
            sprite("empty_tank_input_slot"),
            sprite("empty_tank_output_slot"),
            sprite("empty_battery_input_slot"),
            sprite("empty_battery_output_slot")
    );

    private SophisticatedSlotSpriteGuard() {
    }

    public static void initialize() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        FMLJavaModLoadingContext.get()
                .getModEventBus()
                .addListener(SophisticatedSlotSpriteGuard::onTextureStitch);
    }

    private static void onTextureStitch(TextureStitchEvent.Pre event) {
        if (!InventoryMenu.BLOCK_ATLAS.equals(event.getAtlas().location())) {
            return;
        }
        SLOT_SPRITES.forEach(event::addSprite);
    }

    private static ResourceLocation sprite(String path) {
        return new ResourceLocation(MOD_ID, "item/" + path);
    }
}
