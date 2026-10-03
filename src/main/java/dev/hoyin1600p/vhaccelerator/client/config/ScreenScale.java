package dev.hoyin1600p.vhaccelerator.client.config;

import com.mojang.blaze3d.platform.Window;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Lays the mod's own screens out at one fixed virtual size, so they look the same at every
 * resolution and GUI scale. While a mod-owned screen is current, the window's GUI scale is
 * overridden to fit {@link #VIRTUAL_WIDTH} x {@link #VIRTUAL_HEIGHT}; leaving for any other screen
 * (or none) restores the player's scale through {@code Minecraft.resizeDisplay()}. Hooked from
 * {@code MinecraftScreenScaleMixin}.
 */
public final class ScreenScale {
    public static final int VIRTUAL_WIDTH = 960;
    public static final int VIRTUAL_HEIGHT = 540;

    // Identity set of screens the mod created; weak so closed screens are not kept alive.
    private static final Set<Screen> OWNED = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean overriding;
    private static Screen previous;

    private ScreenScale() {
    }

    /** Marks a screen the mod created; returns it for chaining. */
    public static <T extends Screen> T own(T screen) {
        OWNED.add(screen);
        return screen;
    }

    public static boolean owned(Screen screen) {
        return screen != null && OWNED.contains(screen);
    }

    /**
     * The GUI scale that fits the virtual size into the framebuffer: fractional, limited by the
     * tighter axis, and never below 1 (very small windows get a smaller virtual size instead).
     */
    public static double scaleFor(int framebufferWidth, int framebufferHeight) {
        double scale = Math.min(framebufferWidth / (double) VIRTUAL_WIDTH, framebufferHeight / (double) VIRTUAL_HEIGHT);
        return Math.max(1.0, scale);
    }

    /** {@code Minecraft.setScreen} HEAD: remember which screen is being left. */
    public static void beforeSetScreen(Minecraft minecraft) {
        previous = minecraft.screen;
    }

    /** {@code Minecraft.setScreen} RETURN: apply the override for owned screens, restore it otherwise. */
    public static void afterSetScreen(Minecraft minecraft) {
        Screen current = minecraft.screen;
        // Dialogs Cloth opens from an owned screen (such as "discard changes?") belong to it too.
        if (current != null && !owned(current) && owned(previous) && opensFromOwned(current)) {
            own(current);
        }
        previous = null;
        if (owned(current)) {
            apply(minecraft, current);
        } else if (overriding) {
            overriding = false;
            // Vanilla recomputes the scale from options.guiScale and re-inits the new screen.
            minecraft.resizeDisplay();
        }
    }

    /** {@code Minecraft.resizeDisplay} RETURN: vanilla has reset the scale; re-apply it for owned screens. */
    public static void afterResizeDisplay(Minecraft minecraft) {
        if (owned(minecraft.screen)) {
            apply(minecraft, minecraft.screen);
        }
    }

    private static boolean opensFromOwned(Screen screen) {
        return screen instanceof ConfirmScreen || screen instanceof AlertScreen
                || screen.getClass().getName().startsWith("me.shedaniel.");
    }

    private static void apply(Minecraft minecraft, Screen screen) {
        Window window = minecraft.getWindow();
        double scale = scaleFor(window.getWidth(), window.getHeight());
        overriding = true;
        if (window.getGuiScale() != scale || screen.width != window.getGuiScaledWidth()
                || screen.height != window.getGuiScaledHeight()) {
            window.setGuiScale(scale);
            screen.resize(minecraft, window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }
    }
}
