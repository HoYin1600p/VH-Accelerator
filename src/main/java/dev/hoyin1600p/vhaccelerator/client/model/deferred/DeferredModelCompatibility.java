package dev.hoyin1600p.vhaccelerator.client.model.deferred;

import dev.hoyin1600p.vhaccelerator.client.compat.ctm.CtmModelBakeOptimizer;
import net.minecraftforge.fml.ModList;

/** Shared compatibility gate for every deferred model-loading or baking path. */
public final class DeferredModelCompatibility {
    private DeferredModelCompatibility() {
    }

    /**
     * Block-state deferral and graph skipping: also allowed with CTM when
     * VHA's exact-version CTM bake pass runs, since that pass bakes only the
     * deferred keys CTM wraps and certification refuses CTM-textured blocks.
     */
    public static boolean allowsBlockStateDeferral() {
        ModList mods = ModList.get();
        if (mods == null) {
            return false;
        }
        return !mods.isLoaded("ctm") || CtmModelBakeOptimizer.handlesDeferredBlockStates();
    }

    public static boolean allowsDeferral() {
        ModList mods = ModList.get();
        return allowsDeferral(mods != null, mods != null && mods.isLoaded("ctm"));
    }

    static boolean allowsDeferral(boolean modListKnown, boolean ctmLoaded) {
        // Unknown discovery must fail closed, just like an installed CTM.
        return modListKnown && !ctmLoaded;
    }
}
