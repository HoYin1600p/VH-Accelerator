package dev.hoyin1600p.vhaccelerator.mixin.compat.sophisticated;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Optional;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders Vault's neutral Sigil icon when a Sophisticated memory slot stores
 * only the item type. Sophisticated Core intentionally drops NBT in its
 * default ignore-NBT mode, while Vault's Sigil renderer requires the
 * {@code SigilModel} tag and otherwise selects Minecraft's missing model.
 */
@Mixin(value = StorageScreenBase.class, remap = false)
public abstract class StorageScreenBaseMixin {
    private static final ResourceLocation SIGIL =
            new ResourceLocation("the_vault", "sigil");
    private static final ResourceLocation SIGIL_PLACEHOLDER =
            new ResourceLocation(
                    "the_vault",
                    "textures/gui/slot/sigil_no_item.png"
            );

    @Inject(
            method = "renderSlotBackground",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void vhaccelerator$renderNbtlessSigilMemorySlot(
            PoseStack poseStack,
            Slot slot,
            int x,
            int y,
            CallbackInfo callback
    ) {
        StorageScreenBase<?> screen =
                (StorageScreenBase<?>) (Object) this;
        StorageContainerMenuBase<?> menu = screen.getMenu();
        Optional<ItemStack> memorized =
                menu.getMemorizedStackInSlot(slot.index);
        ItemStack displayStack = memorized.orElseGet(
                () -> menu.getSlotFilterItem(slot.index)
        );
        if (!isNbtlessVaultSigil(displayStack)) {
            return;
        }

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

        poseStack.pushPose();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, GuiHelper.GUI_CONTROLS);
        GuiComponent.blit(
                poseStack,
                x,
                y,
                100,
                77.0F,
                0.0F,
                16,
                16,
                256,
                256
        );
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        poseStack.popPose();
        callback.cancel();
    }

    private static boolean isNbtlessVaultSigil(ItemStack stack) {
        if (stack.isEmpty()
                || !SIGIL.equals(
                        ForgeRegistries.ITEMS.getKey(stack.getItem())
                )) {
            return false;
        }
        return stack.getTag() == null
                || !stack.getTag().contains("SigilModel", 8);
    }
}
