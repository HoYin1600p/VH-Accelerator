package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters.VaultModifierViewCache;
import iskallia.vault.core.vault.Modifiers;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reuses the client's vault modifier views between frames; see
 * VaultModifierViewCache. Only the client and integrated server threads are
 * served from the cache.
 */
@Mixin(value = Modifiers.class, remap = false)
public abstract class ModifiersViewCacheMixin {
    @Inject(method = "getDisplayGroup", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$cachedDisplayGroup(CallbackInfoReturnable<Object2IntMap<?>> callback) {
        if (vhaccelerator$serveFromCache()) {
            Modifiers self = (Modifiers) (Object) this;
            callback.setReturnValue(VaultModifierViewCache.displayGroup(
                    self, self.getEntries(), () -> self.getDisplayGroup()));
        }
    }

    @Inject(method = "getModifiers", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$cachedModifiers(CallbackInfoReturnable<List<?>> callback) {
        if (vhaccelerator$serveFromCache()) {
            Modifiers self = (Modifiers) (Object) this;
            callback.setReturnValue(VaultModifierViewCache.modifiers(
                    self, self.getEntries(), () -> self.getModifiers()));
        }
    }

    private static boolean vhaccelerator$serveFromCache() {
        return !VaultModifierViewCache.computing()
                && vhaccelerator$gameThread()
                && VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.cacheVaultModifierViews, true);
    }

    private static boolean vhaccelerator$gameThread() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            return true;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        return server != null && server.isSameThread();
    }
}
