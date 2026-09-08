package dev.hoyin1600p.vhaccelerator.mixin.client;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.LocalConfigState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes all Forge specs, not just config events on VHA's own mod event bus. */
@Mixin(value = ForgeConfigSpec.class, remap = false)
public abstract class ForgeConfigReloadFingerprintMixin {
    @Shadow private Config childConfig;

    @Inject(method = "afterReload", at = @At("RETURN"))
    private void vhaccelerator$invalidateChangedConfig(CallbackInfo callback) {
        LocalConfigState.changed(childConfig instanceof FileConfig file ? file.getNioPath() : null);
    }
}
