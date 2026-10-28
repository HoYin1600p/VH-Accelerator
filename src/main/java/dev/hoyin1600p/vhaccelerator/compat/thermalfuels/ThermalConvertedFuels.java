package dev.hoyin1600p.vhaccelerator.compat.thermalfuels;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/** Shared entry points of the two Thermal converted-fuel mixins. */
public final class ThermalConvertedFuels {
    public static final String GROUP = "dev.hoyin1600p.vhaccelerator.mixin.compat.thermalfuels";
    public static final String STIRLING_MIXIN = GROUP + ".StirlingConvertedFuelsMixin";
    public static final String GOURMAND_MIXIN = GROUP + ".GourmandConvertedFuelsMixin";
    private static final Set<String> logged = ConcurrentHashMap.newKeySet();

    private ThermalConvertedFuels() { }

    /** Whether the deferral may run now (the whole group passed its layout preflight). */
    public static boolean active(String mixin) {
        return CompatMixinGroups.activationAllowed(mixin);
    }

    /** Records that a refresh skipped the scan; logs once per manager. */
    public static void deferred(String mixin, String manager) {
        if (logged.add(mixin)) {
            VHAccelerator.LOGGER.info("Deferred the Thermal {} converted-fuel scan until a reader asks for the list "
                    + "(only Thermal's JEI plugin does, never a dedicated server)", manager);
        }
        CompatMixinGroups.recordOptimizationUse(mixin);
    }
}
