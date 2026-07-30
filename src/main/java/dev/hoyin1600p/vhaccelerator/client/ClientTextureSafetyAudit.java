package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.ModList;

/**
 * Debug-only verification for sprites registered by identity-sensitive
 * TextureStitchEvent listeners.
 *
 * <p>Several Forge 1.18.2 mods compare the block-atlas identifier with
 * {@code ==}. This audit verifies the known slot, overlay, and dynamic
 * renderer sprites used by supported Vault packs after the atlas has been
 * uploaded. It never changes atlas contents.</p>
 */
public final class ClientTextureSafetyAudit {
    private ClientTextureSafetyAudit() {
    }

    public static void onTextureStitched(TextureStitchEvent.Post event) {
        if (!VHAcceleratorConfig.debugDiagnosticsEnabled()
                || !TextureAtlas.LOCATION_BLOCKS.equals(
                        event.getAtlas().location()
                )) {
            return;
        }

        int expected = 0;
        List<ResourceLocation> missing = new ArrayList<>();
        ResourceLocation missingSprite =
                MissingTextureAtlasSprite.getLocation();
        ModList modList = ModList.get();
        for (SpriteGroup group : ExpectedSprites.GROUPS) {
            if (!modList.isLoaded(group.modId())) {
                continue;
            }
            expected += group.sprites().size();
            for (ResourceLocation sprite : group.sprites()) {
                if (missingSprite.equals(
                        event.getAtlas().getSprite(sprite).getName()
                )) {
                    missing.add(sprite);
                }
            }
        }

        if (missing.isEmpty()) {
            VHAccelerator.LOGGER.info(
                    "Texture safety audit: verified {} fragile block-atlas "
                            + "sprites; none resolved to the missing texture",
                    expected
            );
        } else {
            VHAccelerator.LOGGER.error(
                    "Texture safety audit: {} of {} fragile block-atlas "
                            + "sprites resolved to the missing texture: {}",
                    missing.size(),
                    expected,
                    missing
            );
        }
    }

    private static SpriteGroup group(
            String modId,
            String namespace,
            String... paths
    ) {
        List<ResourceLocation> sprites = new ArrayList<>(paths.length);
        for (String path : paths) {
            sprites.add(new ResourceLocation(namespace, path));
        }
        return new SpriteGroup(modId, List.copyOf(sprites));
    }

    private static SpriteGroup coloredPair(
            String modId,
            String namespace,
            String base,
            List<String> colors
    ) {
        List<ResourceLocation> sprites =
                new ArrayList<>(colors.size() * 2);
        for (String color : colors) {
            sprites.add(new ResourceLocation(
                    namespace,
                    base + "/hammock/" + color
            ));
            sprites.add(new ResourceLocation(
                    namespace,
                    base + "/sleeping_bag/" + color
            ));
        }
        return new SpriteGroup(modId, List.copyOf(sprites));
    }

    private record SpriteGroup(
            String modId,
            List<ResourceLocation> sprites
    ) {
    }

    /**
     * Kept lazy so debug-disabled clients do not allocate the audit tables.
     */
    private static final class ExpectedSprites {
        private static final List<String> VANILLA_COLORS = List.of(
                "white",
                "orange",
                "magenta",
                "light_blue",
                "yellow",
                "lime",
                "pink",
                "gray",
                "light_gray",
                "cyan",
                "purple",
                "blue",
                "brown",
                "green",
                "red",
                "black"
        );
        private static final List<String> DYENAMIC_COLORS = List.of(
                "peach",
                "aquamarine",
                "fluorescent",
                "mint",
                "maroon",
                "bubblegum",
                "lavender",
                "persimmon",
                "cherenkov"
        );

        private static final List<SpriteGroup> GROUPS = List.of(
                group(
                        "the_vault",
                        "the_vault",
                        "gui/slot/coins_no_item",
                        "gui/slot/regret_orb_no_item",
                        "gui/slot/plating_no_item",
                        "gui/slot/jewel_no_item",
                        "gui/slot/silver_scrap_no_item",
                        "gui/slot/tool_no_item",
                        "gui/slot/ember_no_item",
                        "gui/slot/seal_no_item",
                        "gui/slot/augment_no_item",
                        "gui/slot/capstone_no_item",
                        "gui/slot/crystal_no_item",
                        "gui/slot/vault_scrap_no_item",
                        "gui/slot/vault_alloy_no_item",
                        "gui/slot/tool_vise/0_no_item",
                        "gui/slot/tool_vise/1_no_item",
                        "gui/slot/tool_vise/2_no_item",
                        "gui/slot/tool_vise/3_no_item",
                        "gui/slot/tool_vise/4_no_item",
                        "gui/slot/tool_vise/5_no_item",
                        "gui/slot/magnet_table/0_no_item",
                        "gui/slot/magnet_table/1_no_item",
                        "gui/slot/magnet_table/2_no_item",
                        "gui/slot/magnet_table/3_no_item",
                        "gui/slot/bounty_table/empty_pearl",
                        "gui/slot/curios/deck_slot",
                        "gui/slot/curios/vault_compass",
                        "gui/slot/emerald_no_item",
                        "gui/slot/inscription_no_item",
                        "gui/slot/companion_trail_slot",
                        "gui/slot/companion_relic_slot",
                        "gui/slot/companion_ancient_relic_slot",
                        "gui/slot/etching_no_item",
                        "gui/slot/card_no_item",
                        "gui/slot/card_juice_no_item",
                        "gui/slot/vorpal_no_item",
                        "gui/slot/painite_no_item",
                        "gui/slot/sigil_no_item",
                        "gui/slot/unique_shard_no_item"
                ),
                group(
                        "woldsvaults",
                        "woldsvaults",
                        "gui/slot/layout_no_item",
                        "gui/slot/placeholder_no_item"
                ),
                group(
                        "sophisticatedcore",
                        "sophisticatedcore",
                        "item/empty_upgrade_slot",
                        "item/inaccessible_slot",
                        "item/empty_tank_input_slot",
                        "item/empty_tank_output_slot",
                        "item/empty_battery_input_slot",
                        "item/empty_battery_output_slot"
                ),
                group(
                        "sophisticatedstorage",
                        "sophisticatedstorage",
                        "block/lock",
                        "entity/fill_indicators",
                        "item/empty_compression_slot",
                        "item/empty_input_filter_slot",
                        "item/empty_output_filter_slot"
                ),
                group(
                        "curios",
                        "curios",
                        "item/empty_head_slot",
                        "item/empty_necklace_slot",
                        "item/empty_back_slot",
                        "item/empty_body_slot",
                        "item/empty_bracelet_slot",
                        "item/empty_hands_slot",
                        "item/empty_ring_slot",
                        "item/empty_belt_slot",
                        "item/empty_charm_slot",
                        "item/empty_curio_slot",
                        "item/empty_cosmetic_slot"
                ),
                coloredPair(
                        "comforts",
                        "comforts",
                        "entity",
                        VANILLA_COLORS
                ),
                coloredPair(
                        "dyenamicsandfriends",
                        "dyenamicsandfriends",
                        "entity/comforts",
                        DYENAMIC_COLORS
                ),
                group(
                        "architects_palette",
                        "architects_palette",
                        "block/sheet_metal_block_ct"
                ),
                group(
                        "enercell",
                        "enercell",
                        "gui/slot/energy_slot_no_item"
                ),
                group(
                        "lctech",
                        "lctech",
                        "item/empty_fluid_slot",
                        "item/empty_battery_slot"
                ),
                group(
                        "lightmanscurrency",
                        "lightmanscurrency",
                        "item/empty_coin_slot",
                        "item/empty_ticket_slot",
                        "item/empty_wallet_slot",
                        "item/empty_item_slot",
                        "item/empty_upgrade_slot",
                        "item/empty_dye_slot",
                        "item/empty_nbt_highlight",
                        "item/empty_book_slot"
                )
        );

        private ExpectedSprites() {
        }
    }
}
