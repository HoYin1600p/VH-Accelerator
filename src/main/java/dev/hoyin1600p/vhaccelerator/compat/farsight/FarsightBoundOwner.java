package dev.hoyin1600p.vhaccelerator.compat.farsight;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;

/**
 * Decides who bounds Farsight's retained chunks. Vault Render Optimization
 * takes this over once it ships its own bound, which it declares with a
 * marker file in its jar; until then (or without VRO) VH Accelerator does it.
 * Deciding by the marker rather than VRO's presence means a VRO version
 * without the bound never leaves Farsight unbounded.
 */
public final class FarsightBoundOwner {
    public static final String VRO_MARKER = "META-INF/vro-features/farsight-chunk-bound";

    private FarsightBoundOwner() {
    }

    public static boolean farsightLoaded(LoadingModList modList) {
        return modList != null && modList.getModFileById("farsight_view") != null;
    }

    /** True when an installed VRO declares its own Farsight bound. */
    public static boolean vroOwnsBound(LoadingModList modList) {
        if (modList == null) {
            return false;
        }
        ModFileInfo vro = modList.getModFileById("vault_render_optimization");
        if (vro == null) {
            return false;
        }
        try {
            Path marker = vro.getFile().findResource(VRO_MARKER);
            return marker != null && Files.exists(marker);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    public static boolean vhaOwnsBound(LoadingModList modList) {
        return farsightLoaded(modList) && !vroOwnsBound(modList);
    }
}
