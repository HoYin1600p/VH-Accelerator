package dev.hoyin1600p.vhaccelerator.client.compat.thermal;

/**
 * Identifies which synchronized client packet is currently causing Thermal
 * managers to refresh. Thermal's converted furnace fuels depend on item tags,
 * so the recipe-packet pass can defer that work until the immediately
 * following tag-packet pass.
 */
public final class ThermalRefreshPhase {
    private static final int IDLE = 0;
    private static final int RECIPES = 1;
    private static final int TAGS = 2;

    private static volatile int activePhase = IDLE;

    private ThermalRefreshPhase() {
    }

    public static void beginRecipes() {
        activePhase = RECIPES;
    }

    public static void finishRecipes() {
        if (activePhase == RECIPES) {
            activePhase = IDLE;
        }
    }

    public static void beginTags() {
        activePhase = TAGS;
    }

    public static void finishTags() {
        if (activePhase == TAGS) {
            activePhase = IDLE;
        }
    }

    public static boolean isApplyingRecipes() {
        return activePhase == RECIPES;
    }
}
