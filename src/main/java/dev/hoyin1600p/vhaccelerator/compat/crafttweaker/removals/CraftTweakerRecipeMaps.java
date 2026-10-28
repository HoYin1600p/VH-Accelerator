package dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals;

import com.blamejared.crafttweaker.mixin.common.access.recipe.AccessRecipeManager;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.crafting.RecipeManager;

/** Installs observation only at CT's audited fresh-copy publication boundary. */
public final class CraftTweakerRecipeMaps {
    private CraftTweakerRecipeMaps() { }

    public static void publishFreshCopies(RecipeManager manager) {
        boolean enabled = IndexedOutputRemovals.settingEnabled();
        if (!enabled) return;
        boolean fresh = StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                freshCaller(frame.getClassName(), frame.getMethodName())));
        publishFreshCopies(manager, enabled, fresh);
    }

    static boolean freshCaller(String owner, String method) {
        return method.equals("fixRecipeManager") && (owner.equals(
                "com.blamejared.crafttweaker.impl.script.ScriptReloadListener")
                || owner.equals("com.blamejared.crafttweaker.impl.script.RecipeManagerScriptLoader"));
    }

    /** Evidence seam: the production entry point obtains both facts before any map is changed. */
    static void publishFreshCopies(RecipeManager manager, boolean enabled, boolean fresh) {
        if (!enabled || !fresh || !(manager instanceof AccessRecipeManager access)) return;
        var recipes = access.crafttweaker$getRecipes();
        if (recipes.getClass() != HashMap.class) return;
        // Both CT callers have already copied every per-type map, before publishing the manager.
        // Replacing the outer map values keeps RecipeManager and all later RecipeList wrappers coherent.
        recipes.replaceAll((type, map) -> observeFresh(map));
    }

    static <K, V> Map<K, V> observeFresh(Map<K, V> map) {
        return map.getClass() == HashMap.class ? new TrackedRecipeMap<>((HashMap<K, V>) map) : map;
    }
}
