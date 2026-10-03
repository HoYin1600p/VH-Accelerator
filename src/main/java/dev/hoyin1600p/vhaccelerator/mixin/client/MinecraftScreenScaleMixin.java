package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.config.ScreenScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixed virtual size for the mod's own screens; see {@link ScreenScale}. Listed under "client" in
 * the mod's mixin config.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftScreenScaleMixin {
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void vhaccelerator$rememberLeavingScreen(Screen screen, CallbackInfo ci) {
        ScreenScale.beforeSetScreen((Minecraft) (Object) this);
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void vhaccelerator$applyScreenScale(Screen screen, CallbackInfo ci) {
        ScreenScale.afterSetScreen((Minecraft) (Object) this);
    }

    // Window resize, fullscreen toggle and GUI-scale changes all end here: vanilla resets the scale
    // first, then the mod re-applies its own while one of its screens is open.
    @Inject(method = "resizeDisplay", at = @At("RETURN"))
    private void vhaccelerator$keepScreenScale(CallbackInfo ci) {
        ScreenScale.afterResizeDisplay((Minecraft) (Object) this);
    }
}
