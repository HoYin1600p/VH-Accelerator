package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.backport.BackportOwnershipRegistry;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapDebugDiagnostics;
import dev.hoyin1600p.vhaccelerator.client.compat.buildscape.LaunchFasterInteropState;
import dev.hoyin1600p.vhaccelerator.client.compat.ctm.CtmModelBakeOptimizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** The production {@link GateInputs}: real registries, ModernFix queries and state markers. */
public final class LiveGateInputs implements GateInputs {
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");

    private final ModernFixProbe modernFix;
    private boolean reportedModernFixBakeDecision;

    public LiveGateInputs(ModernFixProbe modernFix) {
        this.modernFix = modernFix;
    }

    @Override
    public boolean debugEnabled() {
        return BootstrapDebugDiagnostics.enabled();
    }

    @Override
    public boolean vhaOwns(BackportFeature feature) {
        return BackportOwnershipRegistry.vhaOwns(feature);
    }

    @Override
    public boolean modernFixOptionEnabled(String option) {
        return modernFix.optionEnabled(option);
    }

    @Override
    public boolean modernFixDynamicResourcesEnabled() {
        return modernFix.dynamicResourcesEnabled();
    }

    @Override
    public void buildScapeInteropApplied() {
        LaunchFasterInteropState.markApplied();
    }

    @Override
    public void ctmMixinApplied() {
        CtmModelBakeOptimizer.markMixinApplied();
    }

    @Override
    public void modernFixBakeDecision(boolean dynamicResources) {
        if (!reportedModernFixBakeDecision) {
            reportedModernFixBakeDecision = true;
            if (dynamicResources) {
                LOGGER.warn(
                        "ModernFix dynamic resources are enabled or "
                                + "could not be verified as disabled; "
                                + "VH Accelerator parallel model baking "
                                + "will stay off"
                );
            } else {
                LOGGER.info(
                        "ModernFix dynamic resources are disabled; "
                                + "enabling guarded VH Accelerator "
                                + "parallel model baking"
                );
            }
        }
    }
}
