package dev.hoyin1600p.vhaccelerator.mixin.plugin;

/** The decision part of a {@link MixinGateRule}. */
@FunctionalInterface
interface MixinGate {
    /** The decision, or {@code null} to keep looking at later rules. */
    Boolean decide(GateFacts facts, GateInputs inputs, String mixinClassName);
}
