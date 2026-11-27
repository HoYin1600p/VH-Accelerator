package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Verifies Vault 22.0.1-7237+ bytecode gates for cascade stack indexing and empty ItemStack links. */
class Vault7237GatesTest {
    private static final String EMPTY_STACK_MIXIN =
            "dev.hoyin1600p.vhaccelerator.mixin.compat.leaks.ItemStackEmptyRepresentationMixin";

    @Test
    void cascadeGateSkipsBothModifierGroupAndStacksOfIndexes() {
        assertFalse(ModPresence.cascadeStacksScanned(bytes("\u0000getModifierGroup\u0000")));
        assertFalse(ModPresence.cascadeStacksScanned(bytes("\u0000stackIndex\u0000stacksOf\u0000")));
        assertTrue(ModPresence.cascadeStacksScanned(bytes("\u0000getEntries\u0000getModifiers\u0000")));
    }

    @Test
    void emptyItemStackGateDetectsVault7237MixinMarker() {
        assertTrue(ModPresence.emptyItemStackFixed(
                bytes("\u0000keepEmptyStacksFreeOfEntityLinks\u0000setEntityRepresentation\u0000")));
        assertFalse(ModPresence.emptyItemStackFixed(
                bytes("\u0000useGearRarity\u0000getGearHoverName\u0000")));
    }

    @Test
    void emptyItemStackMixinStepsAsideWhenVaultFixesIt() {
        GateFactsBuilder withoutFix = new GateFactsBuilder();
        withoutFix.physicalClient = true;
        withoutFix.modListKnown = true;
        withoutFix.releaseLevelPinningReferences = true;
        withoutFix.vaultEmptyItemStackFixed = false;

        GateFactsBuilder withFix = new GateFactsBuilder();
        withFix.physicalClient = true;
        withFix.modListKnown = true;
        withFix.releaseLevelPinningReferences = true;
        withFix.vaultEmptyItemStackFixed = true;

        assertTrue(new MixinGateRules(withoutFix.build(), NO_OP_INPUTS).shouldApply(EMPTY_STACK_MIXIN));
        assertFalse(new MixinGateRules(withFix.build(), NO_OP_INPUTS).shouldApply(EMPTY_STACK_MIXIN));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static final GateInputs NO_OP_INPUTS = new GateInputs() {
        @Override
        public boolean debugEnabled() {
            return false;
        }

        @Override
        public boolean vhaOwns(BackportFeature feature) {
            return false;
        }

        @Override
        public boolean modernFixOptionEnabled(String option) {
            return false;
        }

        @Override
        public boolean modernFixDynamicResourcesEnabled() {
            return false;
        }

        @Override
        public void buildScapeInteropApplied() {
        }

        @Override
        public void ctmMixinApplied() {
        }

        @Override
        public void modernFixBakeDecision(boolean dynamicResources) {
        }
    };
}
