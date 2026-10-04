package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import dev.hoyin1600p.vhaccelerator.mixin.plugin.GateScenarios.Facts;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.GateScenarios.Statics;
import java.util.ArrayList;
import java.util.List;

/** Renders the gating snapshot for any implementation of the decision logic. */
final class GateGolden {
    /**
     * Evaluates every mixin name under one scenario. Each result character is
     * '0' or '1' for the decision, or 'b'/'c' (applied) when the decision also
     * marked the BuildScape interop or the CTM bake pass as applied.
     */
    interface Evaluator {
        String decide(Facts facts, Statics statics, List<String> mixinNames);
    }

    private GateGolden() {
    }

    static List<String> render(Evaluator evaluator) {
        List<String> names = GateScenarios.mixinNames();
        List<String> lines = new ArrayList<>();
        lines.add("mixins " + names.size());
        List<Statics> statics = GateScenarios.staticVariants();
        for (Statics variant : statics) {
            lines.add("static " + variant.label());
        }
        for (Facts facts : GateScenarios.factVariants()) {
            StringBuilder line = new StringBuilder("facts ").append(facts.name());
            for (Statics variant : statics) {
                line.append(' ').append(GateScenarios.fingerprint(
                        evaluator.decide(facts, variant, names)));
            }
            lines.add(line.toString());
        }
        List<Statics> singles = GateScenarios.singleOwnershipVariants();
        for (Facts facts : GateScenarios.singleOwnershipFacts()) {
            StringBuilder line = new StringBuilder("single-ownership ").append(facts.name());
            for (Statics variant : singles) {
                line.append(' ').append(GateScenarios.fingerprint(
                        evaluator.decide(facts, variant, names)));
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
