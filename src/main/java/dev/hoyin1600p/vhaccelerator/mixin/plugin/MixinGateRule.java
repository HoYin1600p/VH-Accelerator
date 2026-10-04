package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import java.util.function.Predicate;

/** One matcher on the mixin class name, with the gate that decides for the mixins it matches. */
record MixinGateRule(Predicate<String> matches, MixinGate gate) {
}
