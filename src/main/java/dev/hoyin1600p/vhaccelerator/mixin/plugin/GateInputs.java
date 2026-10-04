package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;

/**
 * The live, process-wide answers the gating rules need beyond {@link GateFacts},
 * plus the hooks they call when a decision has a side effect. Kept behind an
 * interface so the rules can be exercised with scripted answers.
 */
public interface GateInputs {
    boolean debugEnabled();

    boolean vhaOwns(BackportFeature feature);

    /** Whether ModernFix's effective configuration enables {@code option}; false when unknown. */
    boolean modernFixOptionEnabled(String option);

    /** Whether ModernFix dynamic resources are on; true unless verified disabled. */
    boolean modernFixDynamicResourcesEnabled();

    /** The BuildScape launch interop mixin is being applied. */
    void buildScapeInteropApplied();

    /** VHA's CTM bake-pass mixin is being applied. */
    void ctmMixinApplied();

    /** The ModernFix-compatible model baking decision was reached (reported once). */
    void modernFixBakeDecision(boolean dynamicResources);
}
