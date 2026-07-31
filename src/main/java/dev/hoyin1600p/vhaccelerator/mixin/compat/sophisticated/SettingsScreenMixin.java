package dev.hoyin1600p.vhaccelerator.mixin.compat.sophisticated;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hoyin1600p.vhaccelerator.client.compat.sophisticated.VaultSigilSlotRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.client.gui.SettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Extends the neutral Vault Sigil placeholder to Sophisticated Core's
 * dedicated settings screen, which does not use StorageScreenBase's slot
 * background renderer.
 */
@Mixin(value = SettingsScreen.class, remap = false)
public abstract class SettingsScreenMixin {
    @Redirect(
            method = "m_97799_",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/"
                            + "ItemRenderer;m_115203_(Lnet/minecraft/world/"
                            + "item/ItemStack;II)V",
                    remap = false
            )
    )
    private void vhaccelerator$renderNbtlessSigilFilterSlot(
            ItemRenderer itemRenderer,
            ItemStack stack,
            int x,
            int y,
            PoseStack poseStack,
            Slot slot
    ) {
        if (VaultSigilSlotRenderer.isNbtlessVaultSigil(stack)) {
            VaultSigilSlotRenderer.renderPlaceholder(poseStack, x, y);
            return;
        }
        itemRenderer.renderAndDecorateItem(stack, x, y);
    }
}
