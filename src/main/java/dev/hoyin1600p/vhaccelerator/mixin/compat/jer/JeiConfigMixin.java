package dev.hoyin1600p.vhaccelerator.mixin.compat.jer;

import dev.hoyin1600p.vhaccelerator.client.compat.jer.JerCompatibilityCache;
import jeresources.proxy.CommonProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "jeresources.jei.JEIConfig", remap = false)
public abstract class JeiConfigMixin {
    @Redirect(
            method = "registerCategories",
            at = @At(
                    value = "INVOKE",
                    target = "Ljeresources/proxy/CommonProxy;initCompatibility()V",
                    remap = false
            ),
            require = 1
    )
    private void vhaccelerator$reuseCompatibility(CommonProxy proxy) {
        JerCompatibilityCache.ensureInitialized(proxy);
    }
}
