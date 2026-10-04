package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.GateScenarios.Facts;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.GateScenarios.Statics;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the apply/skip decision of every mixin in {@code vhaccelerator.mixins.json}
 * across the environment states in {@link GateScenarios}. The snapshot file was
 * generated from the original if-chain in the mixin plugin before it was split
 * into rules, so a mismatch means a gating decision changed.
 */
class MixinGatingSnapshotTest {
    private static final String SNAPSHOT = "mixin-gating-snapshot.txt";

    @AfterEach
    void removeScriptedModernFix() {
        ModernFixMixinPlugin.uninstall();
    }

    @Test
    void gatingDecisionsMatchTheRecordedSnapshot() throws IOException {
        List<String> expected;
        try (InputStream stream = getClass().getResourceAsStream(SNAPSHOT)) {
            expected = List.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R"));
        }
        assertEquals(expected, GateGolden.render(MixinGatingSnapshotTest::decide));
    }

    private static String decide(Facts facts, Statics statics, List<String> names) {
        statics.modernFix().install();
        GateFacts gateFacts = build(facts.values());
        RecordingInputs inputs = new RecordingInputs(statics, new ModernFixProbe(gateFacts));
        MixinGateRules rules = new MixinGateRules(gateFacts, inputs);
        StringBuilder result = new StringBuilder();
        for (String name : names) {
            inputs.effect = 0;
            char code = rules.shouldApply(name) ? '1' : '0';
            if (inputs.effect != 0) {
                code = inputs.effect;
            }
            result.append(code);
        }
        return result.toString();
    }

    private static GateFacts build(Map<String, Object> values) {
        try {
            GateFactsBuilder builder = new GateFactsBuilder();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                Field field = GateFactsBuilder.class.getDeclaredField(entry.getKey());
                field.setAccessible(true);
                field.set(builder, entry.getValue());
            }
            return builder.build();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Scripted process-wide state; remembers which compat marker a decision set. */
    private static final class RecordingInputs implements GateInputs {
        private final Statics statics;
        private final ModernFixProbe modernFix;
        char effect;

        RecordingInputs(Statics statics, ModernFixProbe modernFix) {
            this.statics = statics;
            this.modernFix = modernFix;
        }

        @Override
        public boolean debugEnabled() {
            return statics.debug();
        }

        @Override
        public boolean vhaOwns(BackportFeature feature) {
            return statics.owned().contains(feature);
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
            effect = 'b';
        }

        @Override
        public void ctmMixinApplied() {
            effect = 'c';
        }

        @Override
        public void modernFixBakeDecision(boolean dynamicResources) {
        }
    }
}
