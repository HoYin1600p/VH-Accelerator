package dev.hoyin1600p.vhaccelerator.client.model;

import net.minecraftforge.fml.ModList;

/** Shared compatibility gate for every deferred model-loading or baking path. */
public final class DeferredModelCompatibility {
    private DeferredModelCompatibility() {
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
