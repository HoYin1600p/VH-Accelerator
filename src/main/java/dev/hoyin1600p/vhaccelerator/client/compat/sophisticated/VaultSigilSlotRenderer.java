package dev.hoyin1600p.vhaccelerator.client.compat.sophisticated;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Shared rendering support for Vault Sigils remembered without their model
 * NBT by Sophisticated Core's default ignore-NBT memory mode.
 */
public final class VaultSigilSlotRenderer {
    private static final ResourceLocation SIGIL =
            new ResourceLocation("the_vault", "sigil");
    private static final ResourceLocation SIGIL_PLACEHOLDER =
            new ResourceLocation(
                    "the_vault",
                    "textures/gui/slot/sigil_no_item.png"
            );

    private VaultSigilSlotRenderer() {
    }

    public static boolean isNbtlessVaultSigil(ItemStack stack) {
        if (stack.isEmpty()
                || !SIGIL.equals(
                        ForgeRegistries.ITEMS.getKey(stack.getItem())
                )) {
            return false;
        }
        return stack.getTag() == null
                || !stack.getTag().contains("SigilModel", 8);
    }

    public static void renderPlaceholder(
            PoseStack poseStack,
            int x,
            int y
    ) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SIGIL_PLACEHOLDER);
        GuiComponent.blit(
                poseStack,
                x,
                y,
                100,
                0.0F,
                0.0F,
                16,
                16,
                16,
                16
        );
    }
}
